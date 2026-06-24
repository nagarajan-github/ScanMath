package com.scanmath.app.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scanmath.app.math.MathParser
import com.scanmath.app.ocr.MathTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ScanState {
    data object Idle : ScanState
    data object Working : ScanState
    data class Done(val rawText: String, val results: List<MathParser.Result>) : ScanState
    data class Failed(val message: String) : ScanState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Shared across both the camera and the permission screen so the gallery
    // path works even if the user declines camera access.
    val recognizer = remember { MathTextRecognizer() }
    var state by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = ScanState.Working
        scope.launch {
            state = try {
                val bitmap = withContext(Dispatchers.Default) { context.loadBitmap(uri) }
                evaluateBitmap(bitmap, recognizer)
            } catch (e: Exception) {
                ScanState.Failed("Couldn't open that image: ${e.message}")
            }
            showSheet = true
        }
    }
    val pickFromGallery = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraScannerContent(
            recognizer = recognizer,
            state = state,
            onStateChange = { state = it },
            onShowSheet = { showSheet = true },
            onPickGallery = pickFromGallery
        )
    } else {
        PermissionRationale(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onPickGallery = pickFromGallery
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; state = ScanState.Idle },
            sheetState = sheetState
        ) {
            ResultSheet(state)
        }
    }
}

@Composable
private fun CameraScannerContent(
    recognizer: MathTextRecognizer,
    state: ScanState,
    onStateChange: (ScanState) -> Unit,
    onShowSheet: () -> Unit,
    onPickGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { CameraController(context) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { previewView ->
                        scope.launch { controller.bind(lifecycleOwner, previewView) }
                    }
                }
            )

            ScanGuideOverlay()

            Text(
                text = "Point at a calculation and tap to solve,\nor pick a photo from your gallery",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ShutterButton(
                working = state is ScanState.Working,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                onClick = {
                    if (state is ScanState.Working) return@ShutterButton
                    onStateChange(ScanState.Working)
                    scope.launch {
                        onStateChange(runScan(controller, recognizer))
                        onShowSheet()
                    }
                }
            )

            GalleryButton(
                enabled = state !is ScanState.Working,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 54.dp),
                onClick = onPickGallery
            )
        }
    }
}

private suspend fun runScan(
    controller: CameraController,
    recognizer: MathTextRecognizer
): ScanState = try {
    val bitmap = controller.capture()
    evaluateBitmap(bitmap, recognizer)
} catch (e: Exception) {
    ScanState.Failed(e.message ?: "Something went wrong while scanning.")
}

/** Run OCR on a bitmap (from camera or gallery) and evaluate any calculation. */
private suspend fun evaluateBitmap(
    bitmap: Bitmap,
    recognizer: MathTextRecognizer
): ScanState = try {
    val text = withContext(Dispatchers.Default) { recognizer.recognize(bitmap) }
    if (text.isBlank()) {
        ScanState.Failed("No text detected. Try a clearer image with good lighting.")
    } else {
        val results = MathParser.parseAndEvaluate(text)
        if (results.isEmpty()) {
            ScanState.Failed("Couldn't find a calculation in:\n\n\"$text\"")
        } else {
            ScanState.Done(text, results)
        }
    }
} catch (e: Exception) {
    ScanState.Failed(e.message ?: "Something went wrong while reading the image.")
}

@Composable
private fun ResultSheet(state: ScanState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
    ) {
        when (state) {
            is ScanState.Done -> {
                Text(
                    "Results",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                state.results.forEach { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                result.expression,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider(Modifier.padding(vertical = 10.dp))
                            if (result.value != null) {
                                Text(
                                    "= ${MathParser.format(result.value)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    result.error ?: "Could not evaluate",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Text(
                    "Scanned text",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    state.rawText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "If a number is missing or wrong, retake the photo closer with even lighting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            is ScanState.Failed -> {
                Text(
                    "Couldn't solve that",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    state.message,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    working: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(76.dp)
    ) {
        if (working) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = "Scan calculation",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun GalleryButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        containerColor = Color(0xCC000000),
        modifier = modifier.size(56.dp)
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = "Pick image from gallery",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun ScanGuideOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .heightIn(min = 180.dp)
                .padding(8.dp)
                .background(Color(0x14FFFFFF), RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun PermissionRationale(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickGallery: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Camera access needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "ScanMath uses the camera to read calculations written on paper and instantly show the answer. " +
                    "You can also solve one from a photo in your gallery — no camera needed.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(onClick = onGrant) { Text("Allow camera") }
            OutlinedButton(
                onClick = onPickGallery,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("  Pick from gallery")
            }
            Text(
                "Open settings",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickableNoRipple(onOpenSettings)
            )
        }
    }
}
