package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal interface PlanningWorkerSubAgent {
    val agentName: String

    fun propose(context: WorkflowAgentContext): PlanningProposalResult
}

internal class RequirementsPlanningSubAgent(
    support: OpenAiWorkflowSupport,
) : BasePlanningWorkerSubAgent(
    support = support,
    agentName = "requirements-planner",
    mission = """
        Смотри на запрос как аналитик требований.
        Проверь, хватает ли входных данных для качественного старта.
        Если данных не хватает, сформулируй минимальные точечные вопросы.
        Если данных достаточно, предложи план без лишних шагов.
    """.trimIndent(),
)

internal class ArchitecturePlanningSubAgent(
    support: OpenAiWorkflowSupport,
) : BasePlanningWorkerSubAgent(
    support = support,
    agentName = "architecture-planner",
    mission = """
        Смотри на запрос как архитектор решения.
        Предлагай план, совместимый с текущей кодовой базой, правилами пользователя и ограничениями workflow.
        Выделяй технические риски и конфликтующие решения, если они есть.
    """.trimIndent(),
)

internal class DeveloperPlanningSubAgent(
    support: OpenAiWorkflowSupport,
) : BasePlanningWorkerSubAgent(
    support = support,
    agentName = "developer-planner",
    mission = """
        Смотри на запрос как разработчик, который будет реализовывать решение в текущей кодовой базе.
        Предлагай минимальный технический путь реализации.
        Отмечай вероятно затрагиваемые модули, переиспользуемые части кода, риски регрессии и нужные тесты.
    """.trimIndent(),
)

internal class DesignerPlanningSubAgent(
    support: OpenAiWorkflowSupport,
) : BasePlanningWorkerSubAgent(
    support = support,
    agentName = "designer-planner",
    mission = """
        Смотри на запрос как дизайнер решения.
        Если задача затрагивает интерфейс, UX, текст, структуру экранов или пользовательский сценарий, предложи план с этой точки зрения.
        Если задача не дизайнерская, кратко отметь это и не добавляй лишние шаги.
    """.trimIndent(),
)

internal abstract class BasePlanningWorkerSubAgent(
    private val support: OpenAiWorkflowSupport,
    final override val agentName: String,
    private val mission: String,
) : PlanningWorkerSubAgent {

    override fun propose(context: WorkflowAgentContext): PlanningProposalResult {
        val prompt = PlanningWorkerPromptBuilder.buildUserPrompt(
            context = context,
            formattedHistory = support.formatHistory(context.history),
        )

        val call = support.complete(
            systemPrompt = PlanningWorkerPromptBuilder.buildSystemPrompt(
                agentName = agentName,
                mission = mission,
            ),
            userPrompt = prompt,
        )

        val payload = support.parseJsonPayload<PlanningWorkerPayload>(call.raw)
            ?: PlanningWorkerPayload(
                summary = "Не удалось надёжно разобрать предложение плана.",
                task = context.state.task.ifBlank { context.userText },
                plan = context.state.plan,
                current = context.state.current,
                questions = listOf("Уточни требования к задаче и ограничения, после этого план можно будет пересобрать."),
                consideredInvariants = emptyList(),
                invariantConflicts = emptyList(),
            )

        return PlanningProposalResult(
            proposal = PlanningProposal(
                agentName = agentName,
                summary = payload.summary.trim(),
                task = payload.task.trim(),
                plan = payload.plan.map(String::trim).filter(String::isNotBlank).distinct(),
                current = payload.current.trim(),
                questions = payload.questions.map(String::trim).filter(String::isNotBlank).distinct(),
                consideredInvariants = payload.consideredInvariants
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
                invariantConflicts = payload.invariantConflicts
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
            ),
            usage = call.usage,
        )
    }

    @Serializable
    private data class PlanningWorkerPayload(
        val summary: String = "",
        val task: String = "",
        val plan: List<String> = emptyList(),
        val current: String = "",
        val questions: List<String> = emptyList(),
        val consideredInvariants: List<String> = emptyList(),
        val invariantConflicts: List<String> = emptyList(),
    )
}

internal object PlanningWorkerPromptBuilder {

    fun buildSystemPrompt(agentName: String, mission: String): String {
        return buildString {
            appendLine("Ты внутренний planning worker `$agentName` в state machine PLANNING -> EXECUTION -> VALIDATION -> DONE.")
            appendLine(mission)
            appendLine()
            appendLine(
                """
                Верни только JSON без markdown:
                {
                  "summary": "короткая сводка для оркестратора",
                  "task": "краткое название задачи",
                  "plan": ["шаг 1", "шаг 2"],
                  "current": "ближайший следующий шаг",
                  "questions": ["точечный вопрос пользователю"],
                  "consideredInvariants": [],
                  "invariantConflicts": []
                }

                Правила:
                - Ты пишешь не пользователю, а planning-orchestrator.
                - Не переходи к выполнению и не проси запускать execution.
                - Если данных не хватает, вопросы должны быть короткими и конкретными.
                - Если план готов, сделай его коротким и исполнимым.
                - "consideredInvariants" включай только для реально применённых правил.
                - "invariantConflicts" включай только для реальных конфликтов.
                - Не игнорируй текущий state, уже предложенный план и историю диалога.
                """.trimIndent()
            )
        }.trim()
    }

    fun buildUserPrompt(context: WorkflowAgentContext, formattedHistory: String): String {
        return buildString {
            appendLine("Профиль пользователя:")
            appendLine(context.userProfileMarkdown.ifBlank { "Профиль пуст" })
            appendLine()
            appendLine("Task context пользователя:")
            appendLine(context.taskContextMarkdown.ifBlank { "Task context пуст" })
            appendLine()
            appendLine("Markdown с правилами пользователя:")
            appendLine(context.invariantMarkdown.ifBlank { "Правила не заданы" })
            appendLine()
            appendLine("Текущее состояние workflow:")
            appendLine("task=${context.state.task}")
            appendLine("state=${context.state.state}")
            appendLine("plan=${context.state.plan.joinToString(prefix = "[", postfix = "]")}")
            appendLine("done=${context.state.done.joinToString(prefix = "[", postfix = "]")}")
            appendLine("current=${context.state.current}")
            appendLine("waitingForUserConfirmation=${context.state.waitingForUserConfirmation}")
            appendLine()
            appendLine("Последняя история диалога:")
            appendLine(formattedHistory)
            appendLine()
            appendLine("Новое сообщение пользователя:")
            appendLine(context.userText)
        }.trim()
    }
}
