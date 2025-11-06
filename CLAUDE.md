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
- **Gemini 2.5 Flash API** (SDK 0.9.0) - Vision + language understanding
- **Coroutines** 1.8.1 - Async/await pattern for Kotlin
- **StateFlow/SnapshotStateList** - Reactive state management
- **Android Gradle Plugin** 8.13.0
- **Compile SDK** 36, **Target SDK** 36, **Min SDK** 24 (Android 7.0+)

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
test/ (or gacch/)
├── app/
│   ├── build.gradle.kts                    # App-level Gradle config
│   └── src/
│       ├── androidTest/java/               # Instrumented tests
│       │   └── com/example/gacch/
│       │       └── ExampleInstrumentedTest.kt
│       ├── test/java/                      # Unit tests
│       │   └── com/example/gacch/
│       │       └── ExampleUnitTest.kt
│       └── main/
│           ├── AndroidManifest.xml         # Permissions: CAMERA, INTERNET
│           └── java/com/example/gacch/
│               ├── MainActivity.kt         # Entry point + UI + Camera handling
│               ├── GeminiVisionClient.kt   # Gemini API wrapper with concurrency
│               ├── ConversationManager.kt  # UI-only chat history manager
│               ├── Main.kt                 # AppScope singleton for coroutines
│               └── ui/theme/               # Compose theme files
│                   ├── Color.kt
│                   ├── Theme.kt
│                   └── Type.kt
├── build.gradle.kts                        # Project-level Gradle config
├── gradle/
│   └── libs.versions.toml                  # Dependency version catalog
├── settings.gradle.kts                     # Gradle settings
└── CLAUDE.md                               # This file
```

**Note:** `assets/` directory does not exist (old TFLite models removed). No deprecated files remain in codebase.

---

## Technical Architecture

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity.kt                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              onCreate() - App Entry Point                 │  │
│  │  • Initialize GeminiVisionClient with API key             │  │
│  │  • Launch Compose UI                                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                             ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         CameraPermissionAndScreen() @Composable          │  │
│  │  • Request CAMERA permission via ActivityResultContract  │  │
│  │  • Show permission UI or launch LiveVisionScreen()       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                             ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            LiveVisionScreen() @Composable                │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  State Management (remember/mutableStateOf)        │  │  │
│  │  │  • imageCapture: ImageCapture?                     │  │  │
│  │  │  • userQuestion: String                            │  │  │
│  │  │  • isProcessing: Boolean                           │  │  │
│  │  │  • lastDescription: String?                        │  │  │
│  │  │  • descriptionCount: Int                           │  │  │
│  │  │  • messages: List<Message> (from Manager)          │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │                                                             │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  UI Layout (Box with Camera + Overlays)            │  │  │
│  │  │  • CameraPreview (full-screen background)          │  │  │
│  │  │  • Top Status Bar (description count, last desc)   │  │  │
│  │  │  • Bottom Chat UI (40% height)                     │  │  │
│  │  │    - LazyColumn (message bubbles)                  │  │  │
│  │  │    - TextField + Send Button                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                             │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  LaunchedEffect(imageCapture) - Background Loop    │  │  │
│  │  │  while(true) {                                      │  │  │
│  │  │    captureAndDescribeImage(...)                    │  │  │
│  │  │  }                                                  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │      CameraPreview() @Composable - AndroidView          │  │
│  │  • Initialize ProcessCameraProvider                      │  │
│  │  • Bind Preview + ImageCapture use cases                 │  │
│  │  • Callback onImageCaptureCreated()                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  captureAndDescribeImage() - Background Task            │  │
│  │  1. delay(1000ms) - wait between captures                │  │
│  │  2. takePicture() - save to MediaStore                   │  │
│  │  3. onImageSaved callback                                │  │
│  │  4. AppScope.launch(Dispatchers.IO) {                    │  │
│  │       • loadBitmapFromUri()                              │  │
│  │       • GeminiVisionClient.addImageToContext(bitmap)     │  │
│  │       • onDescriptionGenerated(description)              │  │
│  │     }                                                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  handleUserQuestion(question) - Foreground Handler      │  │
│  │  1. ConversationManager.addUserMessage(question)         │  │
│  │  2. val result = GeminiVisionClient.askQuestion(...)     │  │
│  │  3. result.fold(                                          │  │
│  │       onSuccess → addAssistantMessage(response)          │  │
│  │       onFailure → addSystemMessage(error)                │  │
│  │     )                                                     │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    GeminiVisionClient.kt                        │
│                         (object singleton)                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Internal State:                                          │  │
│  │  • apiKey: String?                                        │  │
│  │  • model: GenerativeModel?                                │  │
│  │  • continuousChat: Chat? (SINGLE SESSION)                 │  │
│  │  • chatMutex: Mutex (prevents concurrent API calls)       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  initialize(apiKey: String)                               │  │
│  │  • Create GenerativeModel with:                           │  │
│  │    - modelName = "gemini-2.5-flash"                       │  │
│  │    - generationConfig (temp=0.7, topK=40, topP=0.95)      │  │
│  │    - systemInstruction = SYSTEM_PROMPT                    │  │
│  │  • Start continuous chat session                          │  │
│  │  • Called once in MainActivity.onCreate()                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  addImageToContext(image: Bitmap): Result<String>        │  │
│  │  • Try to acquire chatMutex.tryLock()                     │  │
│  │    - If busy, return failure "API busy, skipped"          │  │
│  │  • withTimeout(30000ms) {                                 │  │
│  │      chat.sendMessage(image + "Describe image...")        │  │
│  │    }                                                       │  │
│  │  • Return Result.success(description)                     │  │
│  │  • Always unlock mutex in finally block                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  askQuestion(question: String): Result<String>            │  │
│  │  • chatMutex.lock() - WAIT for lock (user expects answer) │  │
│  │  • withTimeout(30000ms) {                                 │  │
│  │      chat.sendMessage(question)                           │  │
│  │    }                                                       │  │
│  │  • Return Result.success(answer)                          │  │
│  │  • Always unlock mutex in finally block                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  resetSession()                                            │  │
│  │  • Create new chat session: model?.startChat()            │  │
│  │  • Clears all context from Gemini                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  ConversationManager.kt                         │
│                    (object singleton)                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  data class Message(                                      │  │
│  │    role: Role,      // USER, ASSISTANT, SYSTEM            │  │
│  │    text: String,                                          │  │
│  │    timestamp: Long                                        │  │
│  │  )                                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Internal State:                                          │  │
│  │  • _messages: SnapshotStateList<Message>                  │  │
│  │    (Observable by Compose - auto-recomposition)           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Public API:                                              │  │
│  │  • val messages: List<Message> (read-only)                │  │
│  │  • addUserMessage(text: String)                           │  │
│  │  • addAssistantMessage(text: String)                      │  │
│  │  • addSystemMessage(text: String)                         │  │
│  │  • clear()                                                 │  │
│  │  • getRecentMessages(count: Int): List<Message>           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  **IMPORTANT:** This is UI-only. NOT used for Gemini context.  │
│  Background image descriptions are NOT added here.             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         Main.kt                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  object AppScope {                                        │  │
│  │    val coroutineScope = CoroutineScope(Dispatchers.IO)    │  │
│  │  }                                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  • Application-level coroutine scope                           │
│  • Used for background image processing                        │
│  • Lives for entire app lifecycle                             │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow Diagrams

#### Background Mode (Continuous Image Capture + Description)

```
┌─────────────────┐
│ LaunchedEffect  │
│  (imageCapture) │
│   while(true)   │
└────────┬────────┘
         │
         ↓
┌────────────────────────────────────────────────┐
│  captureAndDescribeImage()                     │
│                                                │
│  1. delay(1000ms)                              │
│     ↓                                          │
│  2. imageCapture.takePicture()                 │
│     • Save to MediaStore (Pictures/GACCH/)     │
│     • Returns Uri                              │
│     ↓                                          │
│  3. onImageSaved(Uri)                          │
│     ↓                                          │
│  4. AppScope.launch(Dispatchers.IO) {          │
│       ↓                                        │
│     5. loadBitmapFromUri(uri)                  │
│       ↓                                        │
│     6. GeminiVisionClient.addImageToContext()  │
│        ├─→ Try acquire mutex.tryLock()         │
│        │   └─→ If busy: skip & continue        │
│        ├─→ chat.sendMessage(image + prompt)    │
│        │   └─→ Gemini returns description      │
│        └─→ mutex.unlock()                      │
│       ↓                                        │
│     7. onDescriptionGenerated(description)     │
│        • Update lastDescription state          │
│        • Increment descriptionCount            │
│        • Trigger UI recomposition              │
│     }                                          │
└────────────────────────────────────────────────┘
         │
         ↓ (loop back to step 1)
```

**Key Features:**
- **Non-blocking:** If API is busy, skip current image and continue
- **Error resilient:** Errors don't stop the loop
- **Thread-safe:** Mutex prevents concurrent API calls
- **Timeout protection:** 30-second timeout prevents hanging
- **UI updates:** State changes trigger automatic recomposition

#### Foreground Mode (User Q&A)

```
┌──────────────────────────┐
│  User clicks Send button │
└───────────┬──────────────┘
            │
            ↓
┌────────────────────────────────────────────────┐
│  handleUserQuestion(question: String)          │
│                                                │
│  1. ConversationManager.addUserMessage()       │
│     • Adds to UI chat history                  │
│     • Triggers LazyColumn recomposition        │
│     ↓                                          │
│  2. GeminiVisionClient.askQuestion(question)   │
│     ├─→ mutex.lock() - WAIT if busy            │
│     ├─→ chat.sendMessage(question)             │
│     │   • Gemini has ALL image descriptions    │
│     │   • Synthesizes answer from full context │
│     │   └─→ Returns answer                     │
│     └─→ mutex.unlock()                         │
│     ↓                                          │
│  3. result.fold(                               │
│       onSuccess: {                             │
│         ConversationManager.addAssistantMsg()  │
│         • Shows answer in UI                   │
│       },                                       │
│       onFailure: {                             │
│         ConversationManager.addSystemMessage() │
│         • Shows error in UI                    │
│       }                                        │
│     )                                          │
└────────────────────────────────────────────────┘
            │
            ↓
┌──────────────────────────┐
│  UI updates automatically│
│  (Compose recomposition) │
└──────────────────────────┘
```

**Key Features:**
- **Blocking wait:** User questions wait for API (unlike background mode)
- **Full context:** Gemini has ALL previous image descriptions
- **Error feedback:** Errors shown to user immediately
- **Synchronous UX:** User sees answer before continuing

### Threading Model

```
┌─────────────────────────────────────────────────────────────┐
│                      MAIN THREAD                            │
│  • Compose UI rendering and recomposition                   │
│  • User input handling (TextField, Button clicks)           │
│  • Camera preview rendering (PreviewView)                   │
│  • State updates (mutableStateOf changes)                   │
│  • LaunchedEffect execution context                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ launch(Dispatchers.IO)
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   DISPATCHERS.IO THREADS                    │
│  • Bitmap decoding (loadBitmapFromUri)                      │
│  • Gemini API network calls                                 │
│  • File I/O operations                                      │
│  • Background coroutines (AppScope.launch)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ withContext(Main) or callbacks
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    MAIN EXECUTOR                            │
│  • CameraX callbacks (OnImageSavedCallback)                 │
│  • Camera lifecycle events                                  │
│  • Must NOT block - immediately dispatch to background      │
└─────────────────────────────────────────────────────────────┘
```

**Thread Safety Mechanisms:**
1. **Mutex in GeminiVisionClient:** Prevents concurrent API calls to same Chat session
2. **SnapshotStateList:** Thread-safe observable list in ConversationManager
3. **State hoisting:** All UI state managed in Composable functions (main thread)
4. **Dispatcher switching:** Explicit context switching with `launch(Dispatchers.IO)`

### Concurrency Control (Critical)

The app uses a **Mutex** in `GeminiVisionClient` to prevent race conditions:

```kotlin
private val chatMutex = Mutex()

// Background: Try to acquire, skip if busy
suspend fun addImageToContext(image: Bitmap): Result<String> {
    if (!chatMutex.tryLock()) {
        return Result.failure(Exception("API busy, skipped"))
    }
    try {
        // API call
    } finally {
        chatMutex.unlock()
    }
}

// Foreground: Wait for lock (user expects answer)
suspend fun askQuestion(question: String): Result<String> {
    chatMutex.lock()
    try {
        // API call
    } finally {
        chatMutex.unlock()
    }
}
```

**Why This Matters:**
- Gemini's Chat object is NOT thread-safe
- Concurrent sendMessage() calls could corrupt context
- Background mode: skips images if API busy (performance)
- Foreground mode: waits for lock (user experience)

---

## Critical Implementation Details

### 1. Gemini API Client Configuration

**File:** GeminiVisionClient.kt:52-72

```kotlin
model = GenerativeModel(
    modelName = "gemini-2.5-flash",
    apiKey = apiKey,
    generationConfig = generationConfig {
        temperature = 0.7f      // Balanced creativity (0.0-1.0)
        topK = 40              // Top 40 token sampling
        topP = 0.95f            // Nucleus sampling threshold
        maxOutputTokens = 2048  // Max response length
    },
    systemInstruction = content { text(SYSTEM_PROMPT) }
)

continuousChat = model?.startChat()  // SINGLE persistent session
```

**System Prompt (Lines 28-42):**
```
You are a live vision assistant helping a user understand their
surroundings in real-time.

I will continuously send you images from the camera (approximately
every 1 second). For each image:
1. Describe what you see briefly and concisely (1-2 sentences)
2. Focus on objects, people, text, spatial arrangements
3. Note any changes from previous images

When the user asks a question, answer based on ALL the images you've
seen in this session. You have perfect memory of everything shown to you.

Remember:
- Be concise in descriptions to save context space
- Highlight important details (ingredients, objects, text)
- When answering user questions, synthesize information from all
  images you've seen
```

**Why Single Chat Session:**
- Maintains full conversation history internally
- Each `sendMessage()` has access to ALL previous messages/images
- No manual context management needed
- Context limit: 1,048,576 tokens (~1M)

### 2. API Key Management

**Current State:**
- **Location:** MainActivity.kt:49
- **Value:** `"AIzaSyD0SwqDDNyUEP9jiUwjr9iaUuTtOPJ02Xg"`
- **WARNING:** Hardcoded and visible in source code

**For Production (Options):**

**Option 1: local.properties (Recommended for development)**
```kotlin
// In local.properties (gitignored):
gemini.api.key=AIzaSy...

// In app/build.gradle.kts:
android {
    buildTypes {
        getByName("debug") {
            val properties = Properties()
            properties.load(project.rootProject.file("local.properties").inputStream())
            buildConfigField("String", "GEMINI_API_KEY",
                "\"${properties.getProperty("gemini.api.key")}\"")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

// In MainActivity.kt:
val apiKey = BuildConfig.GEMINI_API_KEY
```

**Option 2: Android Keystore (Recommended for production)**
```kotlin
// Use EncryptedSharedPreferences for runtime storage
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "secret_shared_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**Option 3: Backend proxy (Recommended for public apps)**
- Store API key on your server
- App makes requests to your backend
- Backend forwards to Gemini API
- Prevents key exposure in APK

### 3. Context Window Management

**Gemini 2.5 Flash Specifications:**
- **Max context:** 1,048,576 tokens (~1M)
- **Image tokens:** ~1,290 tokens per 1024x1024 image
- **Text description:** ~100-300 tokens per response
- **User question:** ~10-100 tokens
- **Answer:** ~100-500 tokens

**Capacity Calculation:**
```
Total tokens per image cycle:
  1,290 (image) + 200 (description) = 1,490 tokens/image

Maximum images before limit:
  1,000,000 ÷ 1,490 ≈ 671 images

Realistic usage (5-minute session):
  300 seconds ÷ 1 second = 300 images
  300 × 1,490 = 447,000 tokens (45% of limit)
  Remaining: 553,000 tokens for Q&A

Safe continuous operation: 10-15 minutes before hitting limit
```

**What Happens at Limit:**
- Gemini API returns error: `"Request payload size exceeds the limit"`
- App logs error and continues (doesn't crash)
- **TODO:** Implement automatic session reset

**Future Enhancement:**
```kotlin
var imagesProcessed = 0
val MAX_IMAGES = 500  // Safety threshold

LaunchedEffect(imageCapture) {
    while (true) {
        captureAndDescribeImage(...)
        imagesProcessed++

        if (imagesProcessed >= MAX_IMAGES) {
            GeminiVisionClient.resetSession()
            ConversationManager.addSystemMessage(
                "Memory cleared (session limit reached)"
            )
            imagesProcessed = 0
            descriptionCount = 0
        }
    }
}
```

### 4. Camera Configuration

**CameraX Setup (MainActivity.kt:287-332):**
```kotlin
val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

val preview = Preview.Builder()
    .build()
    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

val imageCapture = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .build()

cameraProvider.bindToLifecycle(
    lifecycleOwner,
    cameraSelector,
    preview,
    imageCapture
)
```

**Image Capture Settings:**
- **Capture mode:** `CAPTURE_MODE_MINIMIZE_LATENCY` (speed over quality)
- **Output format:** JPEG (MediaStore.Images.Media)
- **Save location:** `Pictures/GACCH/` in user's gallery
- **Filename format:** `yyyyMMdd-HHmmss-SSS.jpg`
- **Capture frequency:** 1 second (configurable via delay)

**Image Lifecycle:**
1. Captured by CameraX → saved to MediaStore
2. Loaded as Bitmap → sent to Gemini API
3. Bitmap garbage collected after API call
4. JPEG file remains in user's gallery (persistent)

### 5. Error Handling Strategy

**Background Mode (Resilient):**
```kotlin
// In captureAndDescribeImage():
try {
    val result = GeminiVisionClient.addImageToContext(bitmap)
    result.fold(
        onSuccess = { description ->
            onDescriptionGenerated(description)
        },
        onFailure = { error ->
            Log.e("CameraCapture", "Description failed: ${error.message}", error)
            // DON'T show to user
            // DON'T stop loop
            // Just log and continue
        }
    )
} catch (e: Exception) {
    Log.e("CameraCapture", "Failed to process image", e)
    // Continue loop regardless
}
```

**Foreground Mode (User Feedback):**
```kotlin
// In handleUserQuestion():
result.fold(
    onSuccess = { response ->
        ConversationManager.addAssistantMessage(response)
    },
    onFailure = { error ->
        ConversationManager.addSystemMessage("Error: ${error.message}")
        // User MUST know their question failed
    }
)
```

**Error Types & Handling:**
- **Network errors:** Logged, background continues, foreground shows error
- **API quota exceeded:** Logged, user sees error message
- **Context limit exceeded:** Logged, requires manual reset (future: auto-reset)
- **Camera errors:** Logged, loop continues with next capture
- **Timeout (30s):** API call cancelled, returns error

### 6. Memory Management

**Images:**
- Saved to MediaStore (user's gallery, persistent storage)
- Loaded as Bitmap only during API call
- Bitmap immediately eligible for GC after `addImageToContext()` returns
- **No in-memory image cache**

**Descriptions:**
- Stored in Gemini's server-side context (not local)
- Latest description cached in UI state: `lastDescription: String?` (one string)
- **Total local memory:** ~50-200 bytes per description count

**Conversation History (UI only):**
- ConversationManager stores ALL user/assistant messages
- Each message: ~50-500 bytes (depends on length)
- **Potential issue:** Unbounded growth over time
- **TODO:** Implement max message limit (e.g., keep last 100 messages)

**App Memory Footprint:**
```
Estimated memory usage:
- Compose UI: ~20-50 MB
- CameraX preview: ~10-30 MB
- Single Bitmap during processing: ~4-12 MB (1024x1024 ARGB)
- Conversation history (100 messages): ~50 KB
- Total: ~30-90 MB (typical for Android camera apps)
```

### 7. Cost Analysis

**Gemini 2.5 Flash Pricing (as of January 2025):**
- Input: $0.30 per 1M tokens
- Output: $2.50 per 1M tokens

**Typical 5-Minute Session:**
```
INPUT:
- 300 images × 1,290 tokens = 387,000 tokens
- 300 prompts ("Describe...") × ~10 tokens = 3,000 tokens
- 5 user questions × ~50 tokens = 250 tokens
- Total input: 390,250 tokens
- Cost: 390,250 × $0.30/1M = $0.12

OUTPUT:
- 300 descriptions × 200 tokens = 60,000 tokens
- 5 answers × 400 tokens = 2,000 tokens
- Total output: 62,000 tokens
- Cost: 62,000 × $2.50/1M = $0.16

TOTAL: $0.28 per 5-minute session
```

**Optimization Strategies:**

1. **Reduce capture frequency:**
   ```kotlin
   kotlinx.coroutines.delay(2000)  // 2 seconds instead of 1
   // Savings: 50% reduction → $0.14 per 5 minutes
   ```

2. **Shorter system prompt:**
   ```kotlin
   text("Briefly describe this image in 1 sentence.")
   // Savings: 30-40% output tokens → ~$0.21 per session
   ```

3. **Image downscaling (future enhancement):**
   ```kotlin
   // Resize to 512x512 before API call
   // Token reduction: 75% → $0.09 per session
   ```

4. **Selective capture (motion detection):**
   ```kotlin
   // Only capture when significant change detected
   // Savings: 70-80% → $0.06 per session
   ```

5. **Session limits:**
   ```kotlin
   // Auto-reset after 100 images
   // Prevents runaway costs from forgotten sessions
   ```

---

## Development Configuration

### Gradle Version Catalog (libs.versions.toml)

```toml
[versions]
agp = "8.13.0"
kotlin = "2.0.21"
coreKtx = "1.17.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.9.4"
activityCompose = "1.11.0"
composeBom = "2024.09.00"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### App-Level Dependencies (app/build.gradle.kts)

```kotlin
dependencies {
    val cameraxVersion = "1.3.4"

    // Compose BOM (Bill of Materials - manages Compose versions)
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))

    // Compose core
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Debug tooling (not included in release builds)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // CameraX (all 4 artifacts required)
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Gemini AI SDK (CRITICAL dependency)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

### Android Manifest Permissions

**File:** AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

**Permission Handling:**
- **CAMERA:** Runtime permission requested via `ActivityResultContracts.RequestPermission()` (MainActivity.kt:74-77)
- **INTERNET:** Normal permission, automatically granted at install time
- **WRITE_EXTERNAL_STORAGE:** NOT required (using MediaStore API for Android 10+)

---

## Common Development Tasks

### Adding a "Clear Memory" Button

```kotlin
// In LiveVisionScreen() Composable, add after status bar:
Button(
    onClick = {
        GeminiVisionClient.resetSession()
        ConversationManager.clear()
        descriptionCount = 0
        lastDescription = null
    },
    modifier = Modifier.padding(8.dp)
) {
    Text("Clear Memory")
}
```

### Adjusting Capture Frequency

```kotlin
// In captureAndDescribeImage() function, line 344:
kotlinx.coroutines.delay(2000)  // Change from 1000 to 2000 (2 seconds)
```

### Adding Cost Tracking UI

```kotlin
// In LiveVisionScreen(), add state:
var estimatedCost by remember { mutableStateOf(0.0) }

// Update in LaunchedEffect when description generated:
estimatedCost += 0.00049  // ~$0.49 per 1000 images

// Display in status bar:
Text(
    text = "Estimated cost: $${String.format("%.3f", estimatedCost)}",
    style = MaterialTheme.typography.bodySmall,
    color = Color.Yellow
)
```

### Adding Voice Input

```kotlin
// Add dependency in build.gradle.kts:
implementation("androidx.activity:activity-compose:1.9.3")

// In LiveVisionScreen():
val speechRecognizer = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?: ""

        scope.launch {
            isProcessing = true
            handleUserQuestion(spokenText)
            isProcessing = false
        }
    }
}

// Add microphone button next to Send:
IconButton(
    onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer.launch(intent)
    }
) {
    Icon(Icons.Default.Mic, contentDescription = "Voice input")
}
```

### Implementing Automatic Session Reset

```kotlin
// In LiveVisionScreen(), add state:
var imagesProcessed by remember { mutableStateOf(0) }
val MAX_IMAGES = 500

// Modify LaunchedEffect:
LaunchedEffect(imageCapture) {
    while (true) {
        captureAndDescribeImage(
            imageCapture = capture,
            context = context,
            executor = executor,
            onDescriptionGenerated = { description ->
                lastDescription = description
                descriptionCount++
                imagesProcessed++

                // Auto-reset when approaching limit
                if (imagesProcessed >= MAX_IMAGES) {
                    GeminiVisionClient.resetSession()
                    ConversationManager.addSystemMessage(
                        "Memory cleared (${MAX_IMAGES} images processed)"
                    )
                    imagesProcessed = 0
                    descriptionCount = 0
                }
            }
        )
    }
}
```

---

## Debugging Guide

### Enable Verbose Logging

**In GeminiVisionClient.kt:**
```kotlin
companion object {
    private const val TAG = "GeminiVisionClient"
    private const val VERBOSE = true  // Add this flag
}

// In addImageToContext():
if (VERBOSE) Log.v(TAG, "Acquiring mutex for image description...")
if (VERBOSE) Log.v(TAG, "Description: ${description.take(100)}...")
```

**In MainActivity.kt:**
```kotlin
companion object {
    private const val TAG = "MainActivity"
}

// Add throughout captureAndDescribeImage():
Log.d(TAG, "Starting image capture cycle")
Log.d(TAG, "Image saved to: $uri")
Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}")
```

### Logcat Filters

Create custom filters in Android Studio:
```
GeminiVisionClient  - API calls, responses, mutex operations
CameraCapture       - Photo capture events, errors
LiveVision          - Description count updates, state changes
MainActivity        - Q&A handling, user interactions
```

### Testing with Mock Responses

**Create debug mode in GeminiVisionClient:**
```kotlin
object GeminiVisionClient {
    private const val USE_MOCK_RESPONSES = BuildConfig.DEBUG && false

    suspend fun addImageToContext(image: Bitmap): Result<String> {
        if (USE_MOCK_RESPONSES) {
            delay(500)  // Simulate network latency
            return Result.success("Mock: Kitchen scene detected")
        }
        // Real implementation
    }
}
```

### Common Issues & Solutions

**Issue: "Gemini client not initialized"**
- **Cause:** `GeminiVisionClient.initialize()` not called before use
- **Solution:** Verify line 50 in MainActivity.onCreate() executes before setContent()

**Issue: Descriptions not appearing**
- **Symptoms:** descriptionCount stays at 0, lastDescription is null
- **Debug steps:**
  1. Check Logcat for "CameraCapture" errors
  2. Verify CAMERA permission granted (check app settings)
  3. Verify INTERNET permission in manifest
  4. Test API key in Gemini AI Studio: https://aistudio.google.com/
  5. Check device internet connection

**Issue: "API busy, skipped" messages flooding logs**
- **Cause:** API calls taking longer than 1 second
- **Solutions:**
  1. Increase capture delay: `delay(2000)`
  2. Use faster internet connection
  3. Reduce image resolution
  4. Check Gemini API status: https://status.cloud.google.com/

**Issue: App crashes on question**
- **Symptoms:** App crashes when Send button clicked
- **Solution:** Check that `continuousChat` is not null (GeminiVisionClient.kt:68)

**Issue: High API costs**
- **Investigation:**
  ```kotlin
  var totalApiCalls = 0
  // Track in addImageToContext() and askQuestion()
  Log.i("Cost", "Total API calls: $totalApiCalls")
  ```
- **Mitigation:** Increase delay, implement session limits

**Issue: Context length exceeded**
- **Error:** `"Request payload size exceeds the limit"`
- **Solution:** Implement automatic reset (see "Implementing Automatic Session Reset")

---

## Testing Strategy

### Unit Tests (app/src/test/)

**Test ConversationManager:**
```kotlin
class ConversationManagerTest {
    @Before
    fun setup() {
        ConversationManager.clear()
    }

    @Test
    fun `addUserMessage adds message with USER role`() {
        ConversationManager.addUserMessage("Test question")

        assertEquals(1, ConversationManager.messages.size)
        assertEquals(
            ConversationManager.Message.Role.USER,
            ConversationManager.messages[0].role
        )
        assertEquals("Test question", ConversationManager.messages[0].text)
    }

    @Test
    fun `getRecentMessages returns last N messages`() {
        repeat(10) { i ->
            ConversationManager.addUserMessage("Message $i")
        }

        val recent = ConversationManager.getRecentMessages(3)
        assertEquals(3, recent.size)
        assertEquals("Message 9", recent.last().text)
    }
}
```

### Instrumented Tests (app/src/androidTest/)

**Test Gemini Integration (requires API key):**
```kotlin
@RunWith(AndroidJUnit4::class)
class GeminiVisionClientTest {
    @Before
    fun setup() {
        val apiKey = "test-api-key"  // Use test key
        GeminiVisionClient.initialize(apiKey)
    }

    @Test
    fun testAskQuestion() = runTest {
        val result = GeminiVisionClient.askQuestion("What is 2+2?")

        assertTrue(result.isSuccess)
        val answer = result.getOrNull()
        assertNotNull(answer)
        assertTrue(answer!!.contains("4"))
    }
}
```

**Test UI (Compose):**
```kotlin
@RunWith(AndroidJUnit4::class)
class LiveVisionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSendButtonDisabledWhenQuestionBlank() {
        composeTestRule.setContent {
            GACCHTheme {
                LiveVisionScreen()
            }
        }

        composeTestRule
            .onNodeWithText("Send")
            .assertIsNotEnabled()
    }
}
```

---

## Future Enhancements (Prioritized)

### High Priority

1. **Automatic Context Management**
   - Monitor token usage via API response metadata
   - Auto-reset when approaching 1M token limit
   - Preserve summary of last N descriptions before reset
   - Implement sliding window (keep recent context, discard old)

2. **Error Recovery & Resilience**
   - Retry failed API calls with exponential backoff
   - Offline queue for descriptions (process when online)
   - Graceful degradation when API unavailable
   - Display network status indicator in UI

3. **Cost Monitoring & Alerts**
   - Real-time cost display in UI
   - Set cost alerts (e.g., warn at $1, stop at $5)
   - Usage analytics dashboard
   - Daily/weekly usage reports

### Medium Priority

4. **Voice Interface**
   - Speech-to-text for questions (Android SpeechRecognizer)
   - Text-to-speech for answers (Android TTS)
   - Hands-free operation mode
   - Wake word detection

5. **Session Management**
   - Save/load sessions to local storage
   - Export conversation history (JSON, CSV, TXT)
   - Multiple concurrent sessions (tabs/channels)
   - Cloud backup of sessions

6. **Performance Optimization**
   - Image downscaling before API call (512x512 or 256x256)
   - Batch description generation (every 3-5 images)
   - Caching for repeated questions
   - Compression of saved images

### Low Priority

7. **Advanced Features**
   - Real-time object detection overlay (TensorFlow Lite)
   - Augmented reality annotations (ARCore)
   - Multi-camera support (front + back)
   - Recipe database integration
   - OCR for text extraction
   - Barcode/QR code scanning

8. **UI/UX Improvements**
   - Dark/light theme toggle
   - Customizable capture frequency slider
   - Favorite/bookmark questions
   - Search conversation history
   - Export images with descriptions

---

## Security Considerations

### API Key Protection

**Current Risk:** API key hardcoded in MainActivity.kt:49
**Impact:** Anyone with source code access can use your Gemini quota

**Mitigation Timeline:**
1. **Immediate:** Move to local.properties (gitignore)
2. **Short-term:** Use BuildConfig for different build types
3. **Medium-term:** Implement EncryptedSharedPreferences
4. **Long-term:** Backend proxy for API calls (recommended for production)

### Camera Privacy

**Current Implementation:** All captured images saved to user's gallery
**Consideration:** User may not want 300+ images saved per session

**Options:**
1. **In-memory only:** Don't save to MediaStore, process in memory
   ```kotlin
   imageCapture.takePicture(
       executor,
       object : ImageCapture.OnImageCapturedCallback() {
           override fun onCaptureSuccess(image: ImageProxy) {
               val bitmap = image.toBitmap()  // Process immediately
               // No file saved
           }
       }
   )
   ```

2. **App-private storage:** Save to app's cache directory (auto-deleted on uninstall)
   ```kotlin
   val file = File(context.cacheDir, "temp_image.jpg")
   ```

3. **User preference:** Add toggle in settings
   ```kotlin
   if (userSettings.saveImagesToGallery) {
       // Current implementation
   } else {
       // In-memory processing
   }
   ```

### Network Security

**Current:** HTTPS by default (Gemini SDK handles this)
**Best Practices:**
- Certificate pinning for production apps
- Network security config (networkSecurityConfig.xml)
- Obfuscation with R8/ProGuard in release builds

---

## Known Limitations

1. **No offline mode:** Requires internet for all operations (no local AI model)
2. **No persistence:** All conversation data lost on app restart
3. **Single session:** Can't switch between multiple independent contexts
4. **No image editing:** Can't crop/rotate/adjust before sending to API
5. **Fixed frequency:** 1-second capture interval hardcoded (requires code change)
6. **Linear conversation:** No threading or branching conversations
7. **Memory leak potential:** ConversationManager has unbounded growth
8. **No rate limiting:** Could exceed API quota without warning
9. **No accessibility:** No screen reader support, voice guidance, or TalkBack integration
10. **Android-only:** No iOS, web, or desktop versions

---

## Deprecated/Removed Components

**CONFIRMED: These files do NOT exist in current codebase:**
- ~~`LatestImageManager.kt`~~ - Never integrated, removed
- ~~`ImageEmbedder.kt`~~ - Old MediaPipe implementation, removed
- ~~`InMemoryEmbeddingStore`~~ - Old vector store approach, removed
- ~~`mobilenet_v3_small.tflite`~~ - Old TFLite model, removed
- ~~`assets/` directory~~ - Directory removed (no longer needed)

**Historical Context:**
Initial implementation attempted local embeddings with MediaPipe + TensorFlow Lite, then pivoted to cloud-based Gemini API for simpler architecture and better results.

---

## Getting Help

### For Other Claude Code Instances

1. **Start here:** Read this CLAUDE.md file completely
2. **Check logs:** Use Logcat with filters (GeminiVisionClient, MainActivity, CameraCapture)
3. **Verify setup:**
   - API key set correctly (MainActivity.kt:49)
   - Permissions granted (CAMERA, INTERNET)
   - Device has internet connection
4. **Test incrementally:**
   - First test simple question without images
   - Then test single image description
   - Finally test full continuous mode
5. **Review implementation:** GeminiVisionClient is the core - understand mutex and chat session

### Common Questions (FAQ)

**Q: Why is ConversationManager separate from GeminiVisionClient?**
A: ConversationManager is purely for UI display. Gemini's Chat object manages the actual API context internally - we can't access it, so we mirror user/assistant messages for the UI.

**Q: Where are image descriptions actually stored?**
A: In Gemini's server-side context window (part of the Chat session). NOT stored locally except for displaying the latest one in the status bar.

**Q: Can I access Gemini's full chat history?**
A: No. The Chat object is opaque - we can only send messages and receive responses. We don't have access to the internal history.

**Q: How do I switch to GPT-4 or Claude instead?**
A: Would require replacing entire GeminiVisionClient with OpenAI SDK or Anthropic SDK. Different API structure, different authentication, different message format.

**Q: Can I use this with local AI models (LLaMA, Mistral)?**
A: Not with current architecture. Would need to implement local inference (e.g., with LLaMA.cpp or ONNX Runtime), which is significantly more complex.

**Q: How do I reduce costs?**
A: See "Cost Analysis" section. Quick wins: increase delay to 2-3s, shorter prompts, image downscaling, or selective capture (only when motion detected).

---

## Summary

### What Works (Production-Ready)

✅ **Continuous image capture** (1/second, configurable)
✅ **Gemini description generation** (background, non-blocking)
✅ **User Q&A with full context** (foreground, blocking)
✅ **Real-time UI updates** (Compose reactive state)
✅ **Error handling and logging** (resilient to failures)
✅ **Clean Compose architecture** (declarative UI, proper state management)
✅ **Thread-safe concurrency** (Mutex prevents race conditions)
✅ **Proper permission handling** (runtime CAMERA permission)

### What Needs Improvement

⚠️ **API key security** (hardcoded, should use BuildConfig or backend)
⚠️ **Context limit handling** (no automatic reset at 1M tokens)
⚠️ **Cost monitoring** (no built-in usage tracking)
⚠️ **Offline support** (requires internet, no graceful degradation)
⚠️ **Session persistence** (all data lost on app restart)
⚠️ **Unbounded memory** (ConversationManager grows indefinitely)

### Quick Wins for Next Development Session

1. **Move API key to local.properties** (5 minutes)
   - Add to `.gitignore`
   - Update build.gradle.kts
   - Update MainActivity to use BuildConfig

2. **Add automatic session reset** (10 minutes)
   - Track `imagesProcessed` counter
   - Reset at 500 images (safety threshold)
   - Show system message to user

3. **Implement cost tracking** (15 minutes)
   - Add `estimatedCost` state variable
   - Update on each API call
   - Display in status bar

4. **Add "Clear Memory" button** (5 minutes)
   - Reset GeminiVisionClient session
   - Clear ConversationManager
   - Reset counters

5. **Adjust capture frequency to 2 seconds** (1 minute)
   - Change `delay(1000)` to `delay(2000)`
   - Reduces costs by 50%

---

## Technical Specifications Summary

**Platform:** Android 7.0+ (API 24-36)
**Language:** Kotlin 2.0.21
**UI Framework:** Jetpack Compose (BOM 2024.09.02)
**Camera:** CameraX 1.3.4
**AI Model:** Gemini 2.5 Flash (SDK 0.9.0)
**Concurrency:** Kotlin Coroutines 1.8.1 + Mutex
**Architecture:** MVVM-like (Composable as ViewModel, Manager as Model)
**State Management:** Compose State + SnapshotStateList
**Threading:** Main (UI) + Dispatchers.IO (background)
**Memory:** ~30-90 MB typical usage
**Network:** HTTPS via Gemini SDK
**Storage:** MediaStore for images, no local database

---

This is a **production-ready POC** demonstrating the power of Gemini's large context window for real-time vision understanding. The architecture is intentionally simple for rapid iteration and easy modification.

Last updated: 2025 (based on git commit `ea435f6`)
