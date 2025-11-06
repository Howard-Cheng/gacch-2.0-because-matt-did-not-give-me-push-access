package com.example.gacch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import com.example.gacch.ui.theme.GACCHTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Replace with your actual Gemini API key
        // For production, store this securely (e.g., in local.properties or secrets manager)
        val apiKey = "AIzaSyD0SwqDDNyUEP9jiUwjr9iaUuTtOPJ02Xg" // User will replace this
        GeminiVisionClient.initialize(apiKey)

        setContent {
            GACCHTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraPermissionAndScreen()
                }
            }
        }
    }
}

@Composable
fun CameraPermissionAndScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            LiveVisionScreen()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This app needs camera access for live vision mode.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }) {
                    Text("Grant camera permission")
                }
            }
        }
    }
}

@Composable
fun LiveVisionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var userQuestion by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var lastDescription by remember { mutableStateOf<String?>(null) }
    var descriptionCount by remember { mutableStateOf(0) }

    // Observe conversation messages
    val messages = ConversationManager.messages

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (full screen background)
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onImageCaptureCreated = { imageCapture = it }
        )

        // Background: Capture and describe images every 1 second
        LaunchedEffect(imageCapture) {
            val capture = imageCapture ?: return@LaunchedEffect
            val executor = ContextCompat.getMainExecutor(context)

            while (true) {
                captureAndDescribeImage(
                    imageCapture = capture,
                    context = context,
                    executor = executor,
                    onDescriptionGenerated = { description ->
                        lastDescription = description
                        descriptionCount++
                        Log.d("LiveVision", "Descriptions generated: $descriptionCount")
                    }
                )
            }
        }

        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top status bar showing background activity
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Live Vision Mode Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Descriptions captured: $descriptionCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                    lastDescription?.let { desc ->
                        Text(
                            text = "Last: ${desc.take(60)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom chat interface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                // Conversation history
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = false
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Auto-scroll to bottom when new message arrives
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input field and send button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userQuestion,
                        onValueChange = { userQuestion = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about what you've seen...") },
                        enabled = !isProcessing,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            Log.d("MainActivity", "Send button clicked")
                            if (userQuestion.isNotBlank() && !isProcessing) {
                                val question = userQuestion
                                userQuestion = ""
                                Log.d("MainActivity", "Launching coroutine for question: $question")

                                scope.launch {
                                    Log.d("MainActivity", "Coroutine started")
                                    isProcessing = true
                                    handleUserQuestion(question)
                                    isProcessing = false
                                    Log.d("MainActivity", "Coroutine completed")
                                }
                            } else {
                                Log.d("MainActivity", "Button click ignored: question blank or processing")
                            }
                        },
                        enabled = !isProcessing && userQuestion.isNotBlank()
                    ) {
                        Text(if (isProcessing) "..." else "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ConversationManager.Message) {
    val isUser = message.role == ConversationManager.Message.Role.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when (message.role) {
        ConversationManager.Message.Role.USER -> Color(0xFF007AFF)
        ConversationManager.Message.Role.ASSISTANT -> Color(0xFF34C759)
        ConversationManager.Message.Role.SYSTEM -> Color(0xFFFF9500)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptureCreated: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    onImageCaptureCreated(imageCapture)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

/**
 * Background task: Capture image every 1 second and add description to Gemini context
 */
suspend fun captureAndDescribeImage(
    imageCapture: ImageCapture,
    context: Context,
    executor: Executor,
    onDescriptionGenerated: (String) -> Unit
) {
    // Wait 1 second between each capture
    kotlinx.coroutines.delay(1000)

    val name = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/GACCH"
            )
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Photo capture failed: ${exception.message}", exception)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = output.savedUri ?: return

                // Generate description in background
                AppScope.coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = loadBitmapFromUri(context, uri)

                        // Add image to Gemini's continuous context
                        val result = GeminiVisionClient.addImageToContext(bitmap)

                        result.fold(
                            onSuccess = { description ->
                                onDescriptionGenerated(description)
                            },
                            onFailure = { error ->
                                Log.e("CameraCapture", "Description failed: ${error.message}", error)
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("CameraCapture", "Failed to process image", e)
                    }
                }
            }
        }
    )
}

/**
 * Handle user question - Gemini has all image descriptions in context
 */
suspend fun handleUserQuestion(question: String) {
    Log.d("MainActivity", "handleUserQuestion called with: $question")

    // Add user message to UI conversation
    ConversationManager.addUserMessage(question)
    Log.d("MainActivity", "User message added to conversation")

    // Ask Gemini - it has all image descriptions in its context
    Log.d("MainActivity", "Calling GeminiVisionClient.askQuestion...")
    val result = GeminiVisionClient.askQuestion(question)
    Log.d("MainActivity", "GeminiVisionClient.askQuestion returned")

    result.fold(
        onSuccess = { response ->
            Log.d("MainActivity", "Success! Response: ${response.take(100)}")
            ConversationManager.addAssistantMessage(response)
            Log.d("MainActivity", "Assistant message added to conversation")
        },
        onFailure = { error ->
            Log.e("MainActivity", "Failure! Error: ${error.message}", error)
            val errorMessage = "Error: ${error.message ?: "Unknown error occurred"}"
            ConversationManager.addSystemMessage(errorMessage)
            Log.e("MainActivity", "Gemini API error", error)
        }
    )
    Log.d("MainActivity", "handleUserQuestion completed")
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
