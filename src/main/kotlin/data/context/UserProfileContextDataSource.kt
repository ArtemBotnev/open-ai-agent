package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.UserProfileContext

internal interface UserProfileContextDataSource {
    fun load(): UserProfileContext
    fun save(context: UserProfileContext)
    fun loadMarkdown(): String
}
