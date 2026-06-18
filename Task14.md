# Task 14

## Что изменили

В проекте добавили отдельный слой обязательных правил пользователя, который хранится вне диалога и учитывается на этапе `PLANNING`.

### 1. Отдельный файл правил

Для каждого пользователя теперь используется отдельный файл:

- `context_source/users/<userId>/invariant.md`

Этот файл:

- хранится отдельно от истории сообщений;
- не редактируется агентом;
- используется как внешний источник обязательных правил для планирования.

### 2. Отдельная модель и datasource

Для правил добавили отдельную сущность и отдельный источник данных:

- [Invariant.kt](/src/main/kotlin/domain/context/Invariant.kt)
- [InvariantDataSource.kt](/src/main/kotlin/data/context/InvariantDataSource.kt)
- [MarkdownInvariantDataSource.kt](/src/main/kotlin/data/context/MarkdownInvariantDataSource.kt)

`MarkdownInvariantDataSource` читает markdown-файл и собирает все элементы списков как обязательные правила.

Если `invariant.md` отсутствует, это не считается ошибкой:

- markdown считается пустым;
- список правил считается пустым.

### 3. Подключение в пользовательский контекст

Путь к файлу правил добавлен в конфигурацию контекста пользователя:

- [ContextStorageConfig.kt](/src/main/kotlin/domain/ContextStorageConfig.kt)

Загрузка правила подключена через DI:

- [Koin.kt](/src/main/kotlin/di/Koin.kt)

После этого правила попадают в `WorkflowAgentContext` и доступны planning-агенту как отдельный слой контекста:

- [WorkflowModels.kt](/src/main/kotlin/infra/openai/workflow/WorkflowModels.kt)
- [OpenAiWorkflowCoordinator.kt](/src/main/kotlin/infra/openai/workflow/OpenAiWorkflowCoordinator.kt)

### 4. Учет правил в planning

Для planning-агента был вынесен отдельный builder prompt:

- [PlanningPromptBuilder.kt](/src/main/kotlin/infra/openai/workflow/PlanningPromptBuilder.kt)

Теперь в `PLANNING`:

- markdown с правилами пользователя передаётся отдельно;
- список правил явно включается в system prompt;
- агент обязан учитывать правила при построении плана;
- агент не должен предлагать решения, которые нарушают правила;
- при конфликте запроса с правилами planning должен вернуть отказ от нарушающего варианта.

### 5. Блокировка перехода в execution при конфликте

В coordinator добавлена дополнительная защита:

- если planning вернул конфликт с правилами;
- workflow не переходит в `EXECUTION`;
- пользователю возвращается ответ о конфликте с правилами.

Это сделано в:

- [OpenAiWorkflowCoordinator.kt](/src/main/kotlin/infra/openai/workflow/OpenAiWorkflowCoordinator.kt)

### 6. Терминология для пользователя

Во внутреннем коде и во взаимодействии агентов используется термин `invariant`.

Но в пользовательских формулировках агент использует слово:

- `правило`

Это отражено:

- в тексте `invariant.md`;
- в prompt-инструкциях для `reply`;
- в пользовательских fallback-сообщениях при конфликте.

## Как это сочетается с остальной памятью

Теперь в проекте используются отдельные источники памяти и контекста:

- `generated/users/<userId>/history.json` — история диалога;
- `context_source/users/<userId>/profile.md` — профиль пользователя;
- `context_source/users/<userId>/task_context.md` — рабочий контекст задачи;
- `context_source/users/<userId>/invariant.md` — обязательные правила пользователя;
- `generated/users/<userId>/current_task.json` — состояние workflow.

## Проверка

Для нового слоя добавлены тесты:

- [MarkdownInvariantDataSourceTest.kt](/src/test/kotlin/MarkdownInvariantDataSourceTest.kt)
- [PlanningPromptBuilderTest.kt](/src/test/kotlin/PlanningPromptBuilderTest.kt)

Проверка выполнялась через:

- `./gradlew test`

## Итог

Теперь обязательные правила пользователя:

- хранятся отдельно от диалога;
- не смешиваются с историей сообщений;
- явно передаются в planning;
- учитываются при построении плана;
- могут остановить переход к выполнению, если запрос им противоречит.
