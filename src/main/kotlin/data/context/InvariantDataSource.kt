package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.Invariant

internal interface InvariantDataSource {
    fun load(): Invariant
    fun loadMarkdown(): String
}
