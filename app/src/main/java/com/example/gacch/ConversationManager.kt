package com.example.gacch

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Manages conversation history for multi-turn dialogue with Gemini.
 * Tracks both user questions and assistant responses.
 */
object ConversationManager {

    data class Message(
        val role: Role,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        enum class Role {
            USER,
            ASSISTANT,
            SYSTEM
        }
    }

    private val _messages: SnapshotStateList<Message> = mutableStateListOf()

    /**
     * Get all messages in the conversation
     */
    val messages: List<Message>
        get() = _messages.toList()

    /**
     * Add a user message to the conversation
     */
    fun addUserMessage(text: String) {
        _messages.add(Message(Message.Role.USER, text))
    }

    /**
     * Add an assistant response to the conversation
     */
    fun addAssistantMessage(text: String) {
        _messages.add(Message(Message.Role.ASSISTANT, text))
    }

    /**
     * Add a system message (e.g., errors, notifications)
     */
    fun addSystemMessage(text: String) {
        _messages.add(Message(Message.Role.SYSTEM, text))
    }

    /**
     * Clear all conversation history (e.g., when starting a new session)
     */
    fun clear() {
        _messages.clear()
    }

    /**
     * Get the last N messages for context window management
     */
    fun getRecentMessages(count: Int): List<Message> {
        return _messages.takeLast(count)
    }
}
