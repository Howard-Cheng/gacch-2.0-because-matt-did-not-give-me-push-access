package com.example.gacch

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client for interacting with Gemini 2.5 Flash Vision API.
 * Uses ONE continuous chat session where:
 * - Background: Continuously adds image descriptions
 * - Foreground: User asks questions about what's been seen
 */
object GeminiVisionClient {

    private const val TAG = "GeminiVisionClient"
    private const val MODEL_NAME = "gemini-2.5-flash-lite"

    // Mutex to prevent concurrent requests to the same chat session
    private val chatMutex = Mutex()

    // Flag to signal background processing should pause for user query
    private val userQueryPending = AtomicBoolean(false)

    private const val SYSTEM_PROMPT = """
You are a live vision assistant helping a user understand their surroundings in real-time.

I will continuously send you images from the camera (approximately every 1 second). For each image:
1. Describe what you see briefly and concisely (1-2 sentences)
2. Focus on objects, people, text, spatial arrangements
3. Note any changes from previous images

When the user asks a question, answer based on ALL the images you've seen in this session. You have perfect memory of everything shown to you.

Remember:
- Be concise in descriptions to save context space
- Highlight important details (ingredients, objects, text)
- When answering user questions, synthesize information from all images you've seen
"""

    private var apiKey: String? = null
    private var model: GenerativeModel? = null
    private var continuousChat: Chat? = null

    /**
     * Initialize the Gemini client with your API key.
     * Call this once at app startup.
     */
    fun initialize(apiKey: String) {
        this.apiKey = apiKey

        model = GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            },
            systemInstruction = content { text(SYSTEM_PROMPT) }
        )

        // Start the continuous chat session
        continuousChat = model?.startChat()

        Log.d(TAG, "Gemini Vision Client initialized with model: $MODEL_NAME")
        Log.d(TAG, "Continuous chat session started")
    }

    /**
     * Background: Add image description to continuous chat.
     * Called every ~1 second with the latest camera frame.
     *
     * @param image The captured camera frame
     * @return Description from Gemini, or error
     */
    suspend fun addImageToContext(image: Bitmap): Result<String> {
        return try {
            val chat = continuousChat
                ?: return Result.failure(IllegalStateException("Chat session not initialized"))

            // PRIORITY: Skip if user query is pending
            if (userQueryPending.get()) {
                Log.d(TAG, "User query pending, skipping image")
                return Result.failure(Exception("User query priority, skipped"))
            }

            // Try to acquire lock - if busy, skip this image (don't queue up)
            if (!chatMutex.tryLock()) {
                Log.d(TAG, "API busy, skipping this image")
                return Result.failure(Exception("API busy, skipped"))
            }

            try {
                // Add 30-second timeout to prevent hanging forever
                withTimeout(30000L) {
                    // Ask Gemini to describe this image
                    val response = chat.sendMessage(
                        content {
                            image(image)
                            text("Describe what you see in this image.")
                        }
                    )

                    val description = response.text
                        ?: return@withTimeout Result.failure(Exception("Empty description from Gemini"))

                    Log.d(TAG, "Image described: ${description.take(80)}...")
                    Result.success(description)
                }
            } finally {
                chatMutex.unlock()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating image description", e)
            Result.failure(e)
        }
    }

    /**
     * Foreground: User asks a question about what they've seen.
     * Gemini has all previous image descriptions in context.
     *
     * @param question The user's question
     * @return The assistant's response based on all seen images
     */
    suspend fun askQuestion(question: String): Result<String> {
        return try {
            val chat = continuousChat
                ?: return Result.failure(IllegalStateException("Chat session not initialized"))

            // Set flag to pause background processing
            userQueryPending.set(true)
            Log.d(TAG, "User query priority mode ENABLED")

            try {
                // Wait for lock (user expects an answer)
                // Background loop will see the flag and skip new images
                chatMutex.lock()
                try {
                    // Add 30-second timeout to prevent hanging forever
                    withTimeout(30000L) {
                        // User asks question - Gemini answers from context
                        val response = chat.sendMessage(question)

                        val answer = response.text
                            ?: return@withTimeout Result.failure(Exception("Empty response from Gemini"))

                        Log.d(TAG, "Question answered: ${answer.take(100)}...")
                        Result.success(answer)
                    }
                } finally {
                    chatMutex.unlock()
                }
            } finally {
                // Always clear the flag when done (even on error)
                userQueryPending.set(false)
                Log.d(TAG, "User query priority mode DISABLED")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error answering question", e)
            Result.failure(e)
        }
    }

    /**
     * Reset the continuous chat session (e.g., when starting new live mode)
     */
    fun resetSession() {
        continuousChat = model?.startChat()
        Log.d(TAG, "Chat session reset")
    }

    /**
     * Check if the client is initialized
     */
    fun isInitialized(): Boolean = model != null && continuousChat != null
}
