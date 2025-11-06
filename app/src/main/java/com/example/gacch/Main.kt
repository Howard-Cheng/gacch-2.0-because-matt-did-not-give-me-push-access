package com.example.gacch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Application-level utilities and shared resources.
 */
object AppScope {
    val coroutineScope = CoroutineScope(Dispatchers.IO)
}