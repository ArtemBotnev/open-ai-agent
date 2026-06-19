package com.artembotnev.openai.infra.openai.workflow

internal object PlanningPromptBuilder {

    fun buildSystemPrompt(context: WorkflowAgentContext): String {
        return buildString {
            appendLine(
                """
                Ты planning-orchestrator в state machine с этапами PLANNING -> EXECUTION -> VALIDATION -> DONE.
                Твоя задача:
                - получить предложения плана от четырёх внутренних planning worker-ов;
                - суммаризировать их решения и собрать итоговый план, выбирая лучшую часть из каждого уместного предложения;
                - собрать недостающие требования;
                - предложить пользователю один итоговый план и дождаться явного подтверждения.
                """.trimIndent()
            )
            appendLine()
            appendLine("Обязательные инварианты пользователя. Это жёсткие ограничения, их нельзя нарушать:")
            appendLine(formatInvariants(context))
            appendLine()
            appendLine(
                """
                Отвечай только JSON-объектом без markdown и пояснений вокруг:
                {
                  "reply": "сообщение пользователю",
                  "task": "краткое название задачи",
                  "plan": ["шаг 1", "шаг 2"],
                  "current": "текущий ближайший шаг",
                  "awaitingConfirmation": true,
                  "consideredInvariants": [],
                  "invariantConflicts": []
                }

                Правила:
                - Сначала определи, какие инварианты применимы к запросу пользователя.
                - Всегда учитывай инварианты при выборе архитектуры, технологий, API и бизнес-логики.
                - Не предлагай решения, которые нарушают хотя бы один инвариант.
                - Если запрос конфликтует с инвариантами, в "reply" прямо укажи конфликт, откажись предлагать нарушающий вариант и попроси скорректировать запрос или правила.
                - Если требований не хватает, задай точечные вопросы в "reply", plan можешь оставить пустым или черновым.
                - Если план уже готов, в "reply" покажи только один итоговый план и закончи вопросом: "Принимаем ли мы этот план в реализацию?"
                - В "reply" используй слово "правило" или "правила", а не "инвариант" или "инварианты".
                - В "reply" кратко отметь правила, которые повлияли на план или вызвали конфликт.
                - "consideredInvariants" заполняй только теми инвариантами, которые реально повлияли на решение.
                - "invariantConflicts" заполняй только реальными конфликтами между запросом и инвариантами.
                - Если пользователь внёс правки, обнови итоговый plan и снова запроси подтверждение.
                - Не переходи к кодированию сам. Переход в EXECUTION делает только coordinator после явного подтверждения пользователя.
                - В ответе не показывай все варианты от planning worker-ов. Пользователь должен увидеть только выбранный итог.
                - План должен быть коротким, конкретным, без воды.
                """.trimIndent()
            )
        }.trim()
    }

    fun buildUserPrompt(
        context: WorkflowAgentContext,
        formattedHistory: String,
        proposals: List<PlanningProposal> = emptyList(),
    ): String {
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
            appendLine("Кандидаты от planning worker-ов:")
            if (proposals.isEmpty()) {
                appendLine("- Кандидаты не переданы")
            } else {
                proposals.forEach { proposal ->
                    appendLine("[${proposal.agentName}]")
                    appendLine("summary=${proposal.summary}")
                    appendLine("task=${proposal.task}")
                    appendLine("plan=${proposal.plan.joinToString(prefix = "[", postfix = "]")}")
                    appendLine("current=${proposal.current}")
                    appendLine("questions=${proposal.questions.joinToString(prefix = "[", postfix = "]")}")
                    appendLine("consideredInvariants=${proposal.consideredInvariants.joinToString(prefix = "[", postfix = "]")}")
                    appendLine("invariantConflicts=${proposal.invariantConflicts.joinToString(prefix = "[", postfix = "]")}")
                    appendLine()
                }
            }
            appendLine("Последняя история диалога:")
            appendLine(formattedHistory)
            appendLine()
            appendLine("Новое сообщение пользователя:")
            appendLine(context.userText)
        }.trim()
    }

    private fun formatInvariants(context: WorkflowAgentContext): String {
        return buildString {
            if (context.invariant.isEmpty) {
                appendLine("- Явные правила не заданы")
                return@buildString
            }
            context.invariant.rules.forEach { appendLine("- $it") }
        }.trim()
    }
}
