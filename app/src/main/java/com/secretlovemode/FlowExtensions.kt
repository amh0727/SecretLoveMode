package com.secretlovemode

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

suspend fun Flow<String>.collectToString(): String {
    return this.toList().joinToString("")
}