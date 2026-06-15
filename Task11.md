# Task 11

## Что реализовано

В проекте реализованы три уровня памяти агента:

1. Краткосрочная память
2. Рабочая память
3. Долговременная память

## 1. Краткосрочная память

Краткосрочная память отвечает за текущий диалог и хранится в `generated/history.json`.

### Назначение

- сохранять последние сообщения пользователя и агента;
- передавать историю текущего диалога в LLM;
- ограничивать размер истории по FIFO.

### Особенности

- хранится только последние `20` пользовательских запросов вместе с ответами агента;
- старые записи вытесняются по принципу FIFO;
- используется как история текущего диалога.

### Основные классы

- [AgentHistoryRepository.kt](/src/main/kotlin/domain/repository/AgentHistoryRepository.kt)
- [AgentHistoryDataSource.kt](/src/main/kotlin/data/history/AgentHistoryDataSource.kt)
- [JsonAgentHistoryDataSource.kt](/src/main/kotlin/data/history/JsonAgentHistoryDataSource.kt)
- [AgentHistoryRepositoryJson.kt](/src/main/kotlin/data/history/AgentHistoryRepositoryJson.kt)
- [AppendMessageUseCase.kt](/src/main/kotlin/domain/usecase/AppendMessageUseCase.kt)
- [LoadHistoryUseCase.kt](/src/main/kotlin/domain/usecase/LoadHistoryUseCase.kt)
- [ClearHistoryUseCase.kt](/src/main/kotlin/domain/usecase/ClearHistoryUseCase.kt)

## 2. Рабочая память

Рабочая память отвечает за контекст текущей задачи и хранится в `context_source/task_context.md`.

### Назначение

- удерживать активную задачу пользователя;
- хранить технический контекст по текущему заданию;
- хранить решения, статус и следующие шаги только по текущей задаче.

### Особенности

- относится только к текущему заданию;
- может пересобираться при смене задачи;
- передаётся вместе с каждым пользовательским сообщением в LLM;
- не используется как долговременный профиль пользователя.

### Основные классы

- [TaskContext.kt](/src/main/kotlin/domain/context/TaskContext.kt)
- [TaskContextDataSource.kt](/src/main/kotlin/data/context/TaskContextDataSource.kt)
- [MarkdownTaskContextDataSource.kt](/src/main/kotlin/data/context/MarkdownTaskContextDataSource.kt)
- [TaskContextMemoryUpdater.kt](/src/main/kotlin/domain/context/TaskContextMemoryUpdater.kt)
- [OpenAiTaskContextMemoryUpdater.kt](/src/main/kotlin/infra/task/OpenAiTaskContextMemoryUpdater.kt)

## 3. Долговременная память

Долговременная память отвечает за устойчивый профиль пользователя и хранится в `context_source/user_0_profile.md`.

### Назначение

- хранить базовый профиль пользователя;
- фиксировать знания о пользователе;
- фиксировать устойчивые решения и договорённости;
- использовать профиль как системный контекст для LLM.

### Особенности

- не относится только к одной задаче;
- описывает пользователя в серии диалогов;
- используется как долговременный системный контекст;
- основной профиль в секции `## 1` не редактируется агентом.

### Основные классы

- [UserProfileContext.kt](/src/main/kotlin/domain/context/UserProfileContext.kt)
- [UserProfileContextDataSource.kt](/src/main/kotlin/data/context/UserProfileContextDataSource.kt)
- [MarkdownUserProfileContextDataSource.kt](/src/main/kotlin/data/context/MarkdownUserProfileContextDataSource.kt)
- [UserProfileMemoryUpdater.kt](/src/main/kotlin/domain/context/UserProfileMemoryUpdater.kt)
- [OpenAiUserProfileMemoryUpdater.kt](/src/main/kotlin/infra/profile/OpenAiUserProfileMemoryUpdater.kt)

## Как это используется в LLM

Основная интеграция находится в [OpenAiLlm.kt](/src/main/kotlin/infra/openai/OpenAiLlm.kt).

### Текущее поведение

- `user_0_profile.md` читается как markdown и передаётся в модель как `system`-контекст;
- `task_context.md` читается как markdown и добавляется к каждому пользовательскому сообщению;
- `history.json` хранит историю текущего диалога;
- после каждого ответа обновляются:
  - долговременная память пользователя;
  - рабочая память текущей задачи.

## Архитектурное разделение

### Domain

Содержит контракты и модели:

- `Llm`
- `AgentHistoryRepository`
- `UserProfileContext`
- `TaskContext`
- `UserProfileMemoryUpdater`
- `TaskContextMemoryUpdater`

### Data

Содержит чтение и запись файлов:

- `history.json`
- `user_0_profile.md`
- `task_context.md`

### Infra

Содержит интеграцию с OpenAI:

- `OpenAiLlm`
- `OpenAiUserProfileMemoryUpdater`
- `OpenAiTaskContextMemoryUpdater`

## Итог

В проекте теперь есть разделение памяти по назначению:

- краткосрочная память: история текущего диалога;
- рабочая память: контекст текущей задачи;
- долговременная память: профиль пользователя, знания и решения.

Это даёт более устойчивый контекст для модели и разделяет:

- текущий диалог;
- текущую задачу;
- накопленный профиль пользователя.
