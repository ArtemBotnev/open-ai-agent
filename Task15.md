# Task 15

## Что изменили

Сегодня в проекте переработали этап `PLANNING`: теперь он работает как оркестратор над несколькими planning subagent-ами и не может перейти к выполнению без явного подтверждения пользователя.

### 1. Planning стал оркестратором

`PlanningSubAgent` больше не строит план в одиночку.

Теперь он:

- собирает предложения от нескольких planning worker-ов;
- передаёт их в orchestration prompt;
- суммаризирует их решения;
- собирает один итоговый план для пользователя.

Основные файлы:

- [PlanningSubAgent.kt](/src/main/kotlin/infra/openai/workflow/PlanningSubAgent.kt)
- [PlanningPromptBuilder.kt](/src/main/kotlin/infra/openai/workflow/PlanningPromptBuilder.kt)
- [WorkflowModels.kt](/src/main/kotlin/infra/openai/workflow/WorkflowModels.kt)

### 2. Добавлены planning worker subagent-ы

Для этапа планирования добавили отдельные внутренние subagent-ы в файле:

- [PlanningWorkerSubAgents.kt](/src/main/kotlin/infra/openai/workflow/PlanningWorkerSubAgents.kt)

Сейчас orchestration использует четыре роли:

- `requirements-planner` — проверяет полноту требований и формулирует уточняющие вопросы;
- `architecture-planner` — смотрит на архитектуру и технические ограничения;
- `developer-planner` — предлагает минимальный технический путь реализации, точки переиспользования, риски регрессии и нужные тесты;
- `designer-planner` — добавляет UX/UI и сценарный взгляд, если задача затрагивает интерфейс или пользовательский поток.

Раньше в этой роли был `delivery-planner`, но в ходе доработки его заменили на более полезный для кодовой базы `developer-planner`.

### 3. Итоговый план теперь собирается из лучших частей

В prompt для planning-orchestrator добавили правило:

- не просто выбирать один вариант целиком;
- а суммаризировать решения worker-ов;
- и собирать итоговый план, выбирая лучшую часть из каждого уместного предложения.

Это позволяет не терять сильные стороны отдельных subagent-ов и строить более устойчивый план.

### 4. Переход в execution теперь только после явного approval

В coordinator убрали автоматическое доверие planning-ответу в части старта выполнения.

Теперь переход в `EXECUTION` возможен только если:

- уже есть предложенный план;
- система ждёт подтверждения пользователя;
- пользователь дал явный положительный ответ.

Это реализовано в:

- [OpenAiWorkflowCoordinator.kt](/src/main/kotlin/infra/openai/workflow/OpenAiWorkflowCoordinator.kt)
- [PlanApprovalDecider.kt](/src/main/kotlin/infra/openai/workflow/PlanApprovalDecider.kt)

### 5. Для пользователя зафиксирован один вопрос подтверждения

Если planning подготовил готовый план, пользователю теперь задаётся единый вопрос:

- `Принимаем ли мы этот план в реализацию?`

Если пользователь отвечает положительно, workflow переводится в `EXECUTION`.

Это сделано для того, чтобы переход в выполнение был предсказуемым и не зависел от свободной формулировки planning-ответа.

### 6. Обновлена DI-конфигурация

Новые planning worker-ы подключены через Koin:

- [Koin.kt](/src/main/kotlin/di/Koin.kt)

За счёт этого orchestration получает все planning subagent-ы через DI и использует общий `OpenAiWorkflowSupport`.

## Проверка

Для этих изменений обновили и добавили тесты:

- [PlanningPromptBuilderTest.kt](/src/test/kotlin/PlanningPromptBuilderTest.kt)
- [PlanApprovalDeciderTest.kt](/src/test/kotlin/PlanApprovalDeciderTest.kt)

Проверка выполнялась через:

- `./gradlew test`

## Итог

Сегодня этап планирования стал заметно более зрелым:

- planning теперь работает как оркестратор;
- план строится на основе нескольких специализированных subagent-ов;
- в planning появился отдельный технический взгляд разработчика и отдельный дизайнерский взгляд;
- итоговый план собирается из лучших частей предложений;
- переход к выполнению защищён явным подтверждением пользователя.
