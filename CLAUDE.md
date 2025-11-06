# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GACCH is an Android application that implements a **live vision conversational agent** using Gemini 2.5 Flash's 1M token context window. The app operates in two parallel modes simultaneously:

**Background Mode (Automatic):** Continuously captures photos every 1 second and generates semantic descriptions that accumulate in Gemini's chat context
**Foreground Mode (User-Driven):** User asks questions about what they've seen, and Gemini answers by synthesizing information from ALL accumulated image descriptions

### Core Use Case

Imagine a user walking around their kitchen:
1. **Second 0:** Camera captures → Gemini: "Kitchen counter with cutting board, empty"
2. **Second 1:** Camera captures → Gemini: "Three eggs placed on counter"
3. **Second 2:** Camera captures → Gemini: "Milk bottle visible next to eggs"
4. **Second 3:** Camera captures → Gemini: "Pan on stove, butter visible"
5. **Second 4:** User asks: "What ingredients have I seen?"
6. **Second 5:** Gemini answers: "Based on the images I've observed, you have: 3 eggs, milk, butter, a pan, and a cutting board"
7. **Second 6:** User asks: "Can I make French toast?"
8. **Second 7:** Gemini: "Yes! You have eggs, milk, and butter. You'd just need bread and maybe cinnamon/vanilla for classic French toast"

### Key Innovation: Zero-Database Architecture

**Traditional Approach:**
```
Image → Embedding → Vector DB → Semantic Search → RAG → Answer
```

**Our Approach:**
```
Image → Gemini Description → Gemini Context Window (1M tokens)
User Question → Gemini → Answer (using ALL descriptions in context)
```

**Benefits:**
- ✅ No database setup/maintenance
- ✅ No vector search implementation
- ✅ No embedding generation overhead
- ✅ Gemini handles everything: storage, retrieval, synthesis
- ✅ Perfect for POC/MVP development

### Key Technologies

- **Kotlin** 2.0.21
- **Jetpack Compose** - 100% declarative UI, no XML layouts
- **CameraX** 1.3.4 - Camera2 API wrapper with lifecycle awareness
- **Gemini 2.5 Flash API** - Vision + language understanding
- **Coroutines** 1.8.1 - Async/await pattern for Kotlin
- **StateFlow/SnapshotStateList** - Reactive state management

---

## Build & Run Commands

### Development
```bash
# Windows
gradlew.bat build
gradlew.bat installDebug

# Mac/Linux
./gradlew build
./gradlew installDebug
```

### Testing
```bash
# Unit tests (JVM)
gradlew test

# Instrumented tests (requires connected device/emulator)
gradlew connectedAndroidTest
```

### Cleaning
```bash
# Clean all build artifacts
gradlew clean

# Clean + rebuild
gradlew clean build
```

### APK Generation
```bash
# Debug APK (outputs to app/build/outputs/apk/debug/)
gradlew assembleDebug

# Release APK (requires signing config)
gradlew assembleRelease
```

---

## Project Structure

```
gacch/
├── app/
│   ├── build.gradle.kts          # App-level Gradle config
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml    # Permissions: CAMERA, INTERNET
│           ├── assets/                # (mobilenet_v3_small.tflite can be deleted)
│           └── java/com/example/gacch/
│               ├── MainActivity.kt              # Main entry point + UI
│               ├── GeminiVisionClient.kt        # Gemini API wrapper
│               ├── ConversationManager.kt       # UI-only chat history
│               ├── LatestImageManager.kt        # (DEPRECATED - not used)
│               ├── Main.kt                      # AppScope singleton
│               └── ui/theme/                    # Compose theme files
├── build.gradle.kts              # Project-level Gradle config
├── gradle/
│   └── libs.versions.toml        # Dependency version catalog
├── settings.gradle.kts           # Gradle settings
└── CLAUDE.md                     # This file
```

---

## Architecture Deep Dive

### 1. Entry Point: MainActivity

**File:** [MainActivity.kt](app/src/main/java/com/example/gacch/MainActivity.kt)

**Lifecycle:**
```kotlin
onCreate() {
    // Initialize Gemini with API key (line 49)
    val apiKey = "AIzaSy..." // ACTUAL API KEY PRESENT
    GeminiVisionClient.initialize(apiKey)

    // Start Compose UI
    setContent {
        GACCHTheme {
            CameraPermissionAndScreen()
        }
    }
}
```

**Key Methods:**
- `CameraPermissionAndScreen()` - Line 63: Handles runtime CAMERA permission
- `LiveVisionScreen()` - Line 103: Main UI with dual-mode operation
- `captureAndDescribeImage()` - Line 331: Background loop (every 1s)
- `handleUserQuestion()` - Line 399: Foreground Q&A handler

### 2. Gemini API Client: GeminiVisionClient

**File:** [GeminiVisionClient.kt](app/src/main/java/com/example/gacch/GeminiVisionClient.kt)

**Critical Implementation Details:**

```kotlin
object GeminiVisionClient {
    private var continuousChat: Chat? = null  // ONE chat session for entire app

    fun initialize(apiKey: String) {
        model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f      // Balanced creativity
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            },
            systemInstruction = content { text(SYSTEM_PROMPT) }
        )

        // THIS IS KEY: Single chat session persists
        continuousChat = model?.startChat()
    }

    // Background: Add image descriptions to context
    suspend fun addImageToContext(image: Bitmap): Result<String> {
        val response = continuousChat!!.sendMessage(
            content {
                image(image)
                text("Describe what you see in this image.")
            }
        )
        return Result.success(response.text!!)
    }

    // Foreground: Answer questions from context
    suspend fun askQuestion(question: String): Result<String> {
        // Same chat session - has ALL previous descriptions
        val response = continuousChat!!.sendMessage(question)
        return Result.success(response.text!!)
    }
}
```

**System Prompt (Lines 23-37):**
```
You are a live vision assistant helping a user understand their surroundings in real-time.

I will continuously send you images from the camera (approximately every 1 second). For each image:
1. Describe what you see briefly and concisely (1-2 sentences)
2. Focus on objects, people, text, spatial arrangements
3. Note any changes from previous images

When the user asks a question, answer based on ALL the images you've seen in this session.
You have perfect memory of everything shown to you.

Remember:
- Be concise in descriptions to save context space
- Highlight important details (ingredients, objects, text)
- When answering user questions, synthesize information from all images you've seen
```

**Why This Works:**
- Gemini's Chat object maintains entire conversation history internally
- Each `sendMessage()` call has access to ALL previous messages
- 1M token context window = ~500-1000 image descriptions
- No manual context management needed

### 3. UI Layer: LiveVisionScreen

**File:** [MainActivity.kt:103-249](app/src/main/java/com/example/gacch/MainActivity.kt#L103-L249)

**Compose Hierarchy:**
```
Box (fillMaxSize) {
    CameraPreview (fullScreen background)

    Column (fillMaxSize) {
        // Top Status Bar
        Surface (black 70% opacity) {
            "Live Vision Mode Active"
            "Descriptions captured: 42"
            "Last: Kitchen counter with eggs..."
        }

        Spacer (weight = 1f)  // Pushes chat to bottom

        // Bottom Chat UI (40% height)
        Column (black 80% opacity) {
            LazyColumn {  // Scrollable messages
                MessageBubble(USER, "What ingredients?")
                MessageBubble(ASSISTANT, "I've seen...")
            }

            Row {
                TextField ("Ask about what you've seen...")
                Button ("Send")
            }
        }
    }
}
```

**State Management:**
```kotlin
// Line 107-111: Reactive state variables
var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
var userQuestion by remember { mutableStateOf("") }
var isProcessing by remember { mutableStateOf(false) }
var lastDescription by remember { mutableStateOf<String?>(null) }
var descriptionCount by remember { mutableStateOf(0) }

// Line 113: Observe conversation from ConversationManager
val messages = ConversationManager.messages  // SnapshotStateList - auto-recomposes
```

**Background Loop (Line 124):**
```kotlin
LaunchedEffect(imageCapture) {
    val capture = imageCapture ?: return@LaunchedEffect
    val executor = ContextCompat.getMainExecutor(context)

    while (true) {  // Infinite loop until Composable disposed
        captureAndDescribeImage(
            imageCapture = capture,
            context = context,
            executor = executor,
            onDescriptionGenerated = { description ->
                lastDescription = description
                descriptionCount++  // Triggers recomposition
            }
        )
    }
}
```

### 4. Background Processing: captureAndDescribeImage

**File:** [MainActivity.kt:331-394](app/src/main/java/com/example/gacch/MainActivity.kt#L331-L394)

**Execution Flow:**
```
1. delay(1000ms)                           // Wait 1 second
2. ImageCapture.takePicture()              // CameraX async capture
3. onImageSaved callback (Main thread)
4. AppScope.launch(Dispatchers.IO) {       // Switch to background
5.   loadBitmapFromUri()                   // Decode JPEG
6.   GeminiVisionClient.addImageToContext() // API call (network)
7.   onDescriptionGenerated()              // Update UI state
8. }
```

**Threading:**
- **Line 338:** `delay(1000)` - Suspending function (coroutine-friendly)
- **Line 360:** `takePicture(outputOptions, executor, callback)` - CameraX API
- **Line 372:** `AppScope.coroutineScope.launch(Dispatchers.IO)` - Background thread
- **Line 377:** `GeminiVisionClient.addImageToContext(bitmap)` - Suspend function (network I/O)
- **Line 380:** `onDescriptionGenerated(description)` - Callback to UI (posted to Main)

**Error Handling:**
- Line 365: `onError` - Logs error, continues loop
- Line 384: `catch (e: Exception)` - Logs error, continues loop
- **Critical:** Errors don't crash the app or stop capture loop

### 5. Foreground Handler: handleUserQuestion

**File:** [MainActivity.kt:399-416](app/src/main/java/com/example/gacch/MainActivity.kt#L399-L416)

**Simple Flow:**
```kotlin
suspend fun handleUserQuestion(question: String) {
    // 1. Add to UI conversation (purely visual)
    ConversationManager.addUserMessage(question)

    // 2. Ask Gemini (has ALL image descriptions in context)
    val result = GeminiVisionClient.askQuestion(question)

    // 3. Display response or error
    result.fold(
        onSuccess = { response ->
            ConversationManager.addAssistantMessage(response)
        },
        onFailure = { error ->
            ConversationManager.addSystemMessage("Error: ${error.message}")
        }
    )
}
```

**Why This Is Simple:**
- No need to fetch descriptions from DB
- No need to pass context manually
- Gemini's Chat object already has EVERYTHING
- Just send the question text

### 6. Conversation Manager (UI Only)

**File:** [ConversationManager.kt](app/src/main/java/com/example/gacch/ConversationManager.kt)

**IMPORTANT:** This is NOT used for API context. It's purely for displaying chat bubbles in the UI.

```kotlin
object ConversationManager {
    data class Message(
        val role: Role,       // USER, ASSISTANT, SYSTEM
        val text: String,
        val timestamp: Long
    )

    // SnapshotStateList - Compose observes this automatically
    private val _messages: SnapshotStateList<Message> = mutableStateListOf()

    val messages: List<Message> get() = _messages.toList()

    fun addUserMessage(text: String) {
        _messages.add(Message(Role.USER, text))
    }

    fun addAssistantMessage(text: String) {
        _messages.add(Message(Role.ASSISTANT, text))
    }
}
```

**Why We Still Need This:**
- Gemini's Chat object is internal to the SDK
- We can't access its history for UI display
- So we mirror USER/ASSISTANT messages in ConversationManager
- Background image descriptions are NOT added here (only shown in status bar)

---

## Development Configuration

### Gradle Configuration

**Root build.gradle.kts:**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**App build.gradle.kts:**
```kotlin
android {
    namespace = "com.example.gacch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gacch"
        minSdk = 24        // Android 7.0+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM - manages all Compose versions
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))

    // CameraX - all 4 artifacts required
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Gemini AI SDK - CRITICAL dependency
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

**Runtime Handling:**
- CAMERA: Requested via `ActivityResultContracts.RequestPermission()` in MainActivity.kt:75
- INTERNET: Automatically granted (normal permission)

---

## Critical Implementation Details

### 1. API Key Management

**Current State:**
- API key is HARDCODED in MainActivity.kt:49
- Value: `"AIzaSyD0SwqDDNyUEP9jiUwjr9iaUuTtOPJ02Xg"`
- **WARNING:** This is visible in source code

**For Production:**
```kotlin
// Option 1: local.properties (gitignored)
// In local.properties:
// gemini.api.key=AIzaSy...

// In build.gradle.kts:
android {
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY",
            properties["gemini.api.key"] as String)
    }
}

// In MainActivity.kt:
val apiKey = BuildConfig.GEMINI_API_KEY

// Option 2: Android Keystore
// Use EncryptedSharedPreferences for runtime storage

// Option 3: Environment variable
// Set in CI/CD pipeline, inject at build time
```

### 2. Context Window Management

**Gemini 2.5 Flash Specs:**
- **Max context:** 1,048,576 tokens (~1M)
- **Image tokens:** ~1,290 tokens per 1024x1024 image
- **Description tokens:** ~100-300 tokens per response

**Capacity Calculation:**
```
1,000,000 tokens ÷ (1,290 image + 200 description) ≈ 671 images

Realistically:
- 300 seconds (5 min) = 300 images
- 300 × 1,490 tokens = 447,000 tokens
- Still have 550,000 tokens for questions/answers

Safely: 10-15 minutes of continuous capture before hitting limit
```

**What Happens at Limit:**
- Gemini API returns error: "Context length exceeded"
- App should catch this and call `GeminiVisionClient.resetSession()`
- **TODO:** Implement automatic reset at token threshold

### 3. Threading Model

**Key Dispatchers:**
```kotlin
// Main Thread (UI)
- Compose recomposition
- Camera preview rendering
- User input handling
- State updates

// Main Executor (Camera callbacks)
- ImageCapture.OnImageSavedCallback
- Must NOT do heavy work here

// Dispatchers.IO (Background)
- Bitmap decoding (loadBitmapFromUri)
- Gemini API calls (network I/O)
- File I/O operations

// Dispatchers.Default (not used)
- Could be used for CPU-intensive work
```

**Example Flow:**
```
Main Thread: User clicks Send button
  └─> launch(Dispatchers.Main) {
        handleUserQuestion(question)  // suspend fun
          └─> GeminiVisionClient.askQuestion()  // suspend fun
                └─> Gemini SDK internally uses Dispatchers.IO
                      └─> withContext(Dispatchers.Main) {
                            ConversationManager.addAssistantMessage()
                              └─> Compose recomposition triggered
                          }
      }
```

### 4. Error Handling Strategy

**Background Loop:**
```kotlin
try {
    val result = GeminiVisionClient.addImageToContext(bitmap)
    result.fold(
        onSuccess = { /* Update UI */ },
        onFailure = { error ->
            Log.e("TAG", "Description failed", error)
            // DON'T show error to user
            // DON'T stop the loop
            // Just log and continue
        }
    )
} catch (e: Exception) {
    Log.e("TAG", "Unexpected error", e)
    // Continue loop
}
```

**Foreground Q&A:**
```kotlin
result.fold(
    onSuccess = { response ->
        ConversationManager.addAssistantMessage(response)
    },
    onFailure = { error ->
        // DO show error to user
        ConversationManager.addSystemMessage("Error: ${error.message}")
        // User needs to know their question failed
    }
)
```

**Rationale:**
- Background errors shouldn't interrupt continuous capture
- Foreground errors need user feedback
- All errors logged to Logcat for debugging

### 5. Memory Management

**Images:**
- Each captured image saved to MediaStore (persistent)
- Loaded as Bitmap for API call
- Bitmap discarded after API call (GC'd)
- No local image storage in app memory

**Descriptions:**
- Stored in Gemini's context (server-side)
- Not duplicated locally (except UI display)
- Latest description shown in status bar (1 string)

**Conversation:**
- ConversationManager keeps full history (UI only)
- Could grow large over time
- **TODO:** Implement max message limit (e.g., 100 messages)

### 6. Cost Optimization

**Current Cost (Typical 5-min session):**
```
Input:
- 300 images × 1,290 tokens = 387,000 tokens
- 5 questions × ~50 tokens = 250 tokens
- Total input: ~387,250 tokens
- Cost: 387,250 × $0.30/1M = $0.12

Output:
- 300 descriptions × 200 tokens = 60,000 tokens
- 5 answers × 400 tokens = 2,000 tokens
- Total output: 62,000 tokens
- Cost: 62,000 × $2.50/1M = $0.16

Total: $0.28 per 5-minute session
```

**Optimization Strategies:**
1. **Reduce frequency:** 2-3 seconds instead of 1 → 50-66% cost savings
2. **Shorter descriptions:** "Be extremely brief" in prompt → 30-40% savings
3. **Selective capture:** Only capture when motion detected → 70-80% savings
4. **Session limits:** Auto-reset after 100 images → prevents runaway costs
5. **Image downscaling:** 512x512 instead of 1024x1024 → 75% token savings

---

## Common Development Tasks

### Adding New Features

**To add a "Clear Memory" button:**
```kotlin
// In LiveVisionScreen Composable:
Button(onClick = {
    GeminiVisionClient.resetSession()
    ConversationManager.clear()
    descriptionCount = 0
    lastDescription = null
}) {
    Text("Clear Memory")
}
```

**To adjust capture frequency:**
```kotlin
// In captureAndDescribeImage function, line 338:
kotlinx.coroutines.delay(2000)  // Change from 1000 to 2000 (2 seconds)
```

**To add voice input:**
```kotlin
// Add dependency:
implementation("androidx.activity:activity-compose:1.9.3")

// In LiveVisionScreen:
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    val spokenText = result.data?.getStringExtra(RecognizerIntent.EXTRA_RESULTS)
    // Send to handleUserQuestion()
}
```

### Debugging

**Enable verbose logging:**
```kotlin
// In GeminiVisionClient:
private const val TAG = "GeminiVisionClient"

// Add more logging:
Log.v(TAG, "Sending image to Gemini...")
Log.v(TAG, "Response: $description")
Log.v(TAG, "Context size: ${chat.history.size} messages")
```

**Monitor Logcat filters:**
```
GeminiVisionClient  - API calls and responses
CameraCapture      - Photo capture events
LiveVision         - Description count updates
MainActivity       - Q&A handling
```

**Test with mock responses:**
```kotlin
// In GeminiVisionClient for testing:
suspend fun addImageToContext(image: Bitmap): Result<String> {
    if (BuildConfig.DEBUG) {
        delay(500)  // Simulate network
        return Result.success("Mock description: Test object detected")
    }
    // Real implementation
}
```

### Testing

**Unit Test Example:**
```kotlin
@Test
fun `ConversationManager adds messages correctly`() {
    ConversationManager.clear()
    ConversationManager.addUserMessage("Test question")

    assertEquals(1, ConversationManager.messages.size)
    assertEquals(Role.USER, ConversationManager.messages[0].role)
    assertEquals("Test question", ConversationManager.messages[0].text)
}
```

**Integration Test (requires device):**
```kotlin
@RunWith(AndroidJUnit4::class)
class GeminiIntegrationTest {
    @Test
    fun testApiConnection() = runTest {
        GeminiVisionClient.initialize("test-key")
        val result = GeminiVisionClient.askQuestion("What is 2+2?")
        assertTrue(result.isSuccess)
    }
}
```

---

## Troubleshooting

### Issue: "Gemini client not initialized"

**Cause:** `GeminiVisionClient.initialize()` not called before use

**Solution:**
```kotlin
// In MainActivity.onCreate():
GeminiVisionClient.initialize(apiKey)  // Must be before setContent
```

### Issue: Descriptions not appearing

**Symptoms:**
- Description count stays at 0
- Status bar shows "Last: null"

**Debug Steps:**
1. Check Logcat for "CameraCapture" errors
2. Verify CAMERA permission granted
3. Check INTERNET permission in manifest
4. Look for network errors in Logcat
5. Verify API key is valid (test in Gemini AI Studio)

**Common Causes:**
- API key invalid/expired
- No internet connection
- Camera permission denied
- CameraX initialization failed

### Issue: App crashes on question

**Symptoms:**
- App crashes when Send button clicked
- "Chat session not initialized" error

**Solution:**
```kotlin
// Verify in GeminiVisionClient.initialize():
continuousChat = model?.startChat()  // Must not be null
```

### Issue: High API costs

**Symptoms:**
- Unexpected bill from Google AI
- Costs exceeding estimates

**Investigation:**
```kotlin
// Add cost tracking:
var totalImages = 0
var totalQuestions = 0

// In captureAndDescribeImage:
totalImages++
Log.i("Cost", "Total images: $totalImages, Est cost: $${totalImages * 0.000516}")

// In handleUserQuestion:
totalQuestions++
Log.i("Cost", "Total questions: $totalQuestions")
```

**Mitigation:**
- Reduce capture frequency
- Set daily session limit
- Implement auto-reset after N images

### Issue: Context length exceeded

**Error:** `"Request payload size exceeds the limit"`

**Solution:**
```kotlin
// Add automatic reset:
var imagesProcessed = 0

LaunchedEffect(imageCapture) {
    while (true) {
        captureAndDescribeImage(...)
        imagesProcessed++

        if (imagesProcessed >= 500) {  // Safety limit
            GeminiVisionClient.resetSession()
            ConversationManager.addSystemMessage("Memory cleared (session limit)")
            imagesProcessed = 0
        }
    }
}
```

---

## Future Enhancements (Prioritized)

### High Priority

1. **Automatic Context Management**
   - Monitor token usage
   - Auto-reset when approaching 1M limit
   - Preserve last N descriptions before reset

2. **Error Recovery**
   - Retry failed API calls (exponential backoff)
   - Offline queue for descriptions
   - Graceful degradation when API unavailable

3. **Cost Monitoring**
   - Display estimated cost in UI
   - Set cost alerts
   - Usage analytics dashboard

### Medium Priority

4. **Voice Input**
   - Speech-to-text for questions
   - Text-to-speech for answers
   - Hands-free operation mode

5. **Session Management**
   - Save/load sessions
   - Export conversation history
   - Multiple concurrent sessions

6. **Performance Optimization**
   - Image downscaling before API call
   - Batch description generation
   - Caching for repeated questions

### Low Priority

7. **Advanced Features**
   - Real-time object highlighting
   - Augmented reality overlays
   - Multi-camera support
   - Recipe database integration

---

## Security Considerations

### API Key Protection

**Current Risk:** API key in source code
**Impact:** Anyone with repository access can use your Gemini quota

**Mitigation:**
1. Move to local.properties (immediate)
2. Use BuildConfig (medium-term)
3. Implement server-side proxy (production)

### Camera Privacy

**Current:** All images saved to MediaStore (user's photo gallery)
**Consideration:** User may not want all frames saved

**Options:**
1. Use in-memory cache only (delete after API call)
2. Add "Save photos" toggle in settings
3. Store in app-private directory (auto-deleted on uninstall)

### Network Security

**Current:** HTTPS by default (Gemini SDK handles this)
**Best Practice:** Certificate pinning for production

---

## Known Limitations

1. **No offline mode:** Requires internet for all operations
2. **No persistence:** All data lost on app restart
3. **Single session:** Can't switch between multiple contexts
4. **No image editing:** Can't crop/rotate before sending
5. **Fixed frequency:** 1-second capture interval hardcoded
6. **No conversation threading:** Linear chat only
7. **Memory leak potential:** ConversationManager unbounded growth
8. **No rate limiting:** Could exceed API quotas
9. **No accessibility:** No screen reader support yet
10. **Android-only:** No iOS version

---

## Removed/Deprecated Components

**DO NOT USE:**
- ~~`LatestImageManager.kt`~~ - Created but never integrated, can delete
- ~~`ImageEmbedder.kt`~~ - Old MediaPipe implementation, replaced by Gemini
- ~~`InMemoryEmbeddingStore`~~ - Old vector store, replaced by Gemini context
- ~~`mobilenet_v3_small.tflite`~~ - Old TFLite model in assets/, safe to delete

**Why they exist:** Initial implementation used local embeddings, pivoted to cloud-based descriptions

---

## Getting Help

**If you're another Claude Code instance:**
1. Read this file completely first
2. Check Logcat for errors
3. Verify API key is set correctly
4. Test with simple questions first
5. Review GeminiVisionClient implementation if issues persist

**Common questions:**
- Q: Why is ConversationManager separate from GeminiVisionClient?
  A: ConversationManager is UI-only. Gemini's Chat object manages API context internally.

- Q: Where are image descriptions stored?
  A: In Gemini's server-side context window. Not stored locally.

- Q: Can I access Gemini's chat history?
  A: No, the Chat object is opaque. We only see responses, not full history.

- Q: How do I change the model to GPT-4?
  A: Would require replacing entire GeminiVisionClient with OpenAI SDK. Different API structure.

---

## Summary

**What works:**
✅ Continuous image capture (1/second)
✅ Gemini description generation (background)
✅ User Q&A with full context (foreground)
✅ Real-time UI updates
✅ Error handling and logging
✅ Clean Compose architecture

**What needs improvement:**
⚠️ API key security
⚠️ Context limit handling
⚠️ Cost monitoring
⚠️ Offline support
⚠️ Session persistence

**Quick wins for next session:**
1. Move API key to local.properties
2. Add automatic session reset at 500 images
3. Implement cost tracking in UI
4. Add "Clear Memory" button
5. Adjust capture frequency to 2 seconds

This is a functional POC demonstrating the power of Gemini's large context window for real-time vision understanding. The architecture is intentionally simple for rapid iteration.
