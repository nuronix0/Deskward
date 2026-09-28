package com.homeport.app.ui.screens.devices

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.homeport.app.ui.theme.SoftEmerald
import com.homeport.app.ui.theme.TextPrimary
import com.homeport.app.ui.theme.TextSecondary
import java.nio.ByteBuffer
import java.util.concurrent.Executors

data class ScannedPairData(
    val ip: String?,
    val port: Int,
    val deviceId: String?,
    val deviceName: String?,
    val secret: String,
    val rawText: String,
    val altIps: List<String> = emptyList(),
    val relayId: String? = null,           // Global relay device ID (= Desktop's deviceId)
    val signalingUrl: String? = null       // Signaling server WebSocket URL
)

@Composable
fun CameraQrScannerView(
    onQrScanned: (ScannedPairData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isDecoding by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        isDecoding = false
        onDispose {
            isDecoding = false
        }
    }

    // Gallery Photo Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val scanned = decodeQrFromBitmap(bitmap)
                    if (scanned != null) {
                        onQrScanned(scanned)
                    } else {
                        errorMessage = "No valid QR code found in selected photo"
                    }
                } else {
                    errorMessage = "Unable to load image"
                }
            } catch (e: Exception) {
                errorMessage = "Error reading photo: ${e.localizedMessage}"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scanAnimation = rememberInfiniteTransition(label = "Laser")
    val laserOffset by scanAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F1118))
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(1280, 720))
                            .build()

                        val executor = Executors.newSingleThreadExecutor()
                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(
                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
                                DecodeHintType.CHARACTER_SET to "UTF-8"
                            ))
                        }

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            try {
                                if (!isDecoding) {
                                    val scanned = analyzeImageProxy(imageProxy, reader)
                                    if (scanned != null) {
                                        isDecoding = true
                                        previewView.post {
                                            onQrScanned(scanned)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = cam.cameraControl
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dim overlay around target reticle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            )

            // Central viewfinder cutout
            val targetSize = 220.dp
            Box(
                modifier = Modifier
                    .size(targetSize)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, SoftEmerald.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // Animated Laser Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(2.dp)
                        .offset(y = ((laserOffset - 0.5f) * 200).dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    SoftEmerald,
                                    Color.White,
                                    SoftEmerald,
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Corner indicators
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Text(
                        "POINT AT QR CODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Controls: Torch + Gallery import
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Torch toggle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (torchEnabled) SoftEmerald else Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            torchEnabled = !torchEnabled
                            cameraControl?.enableTorch(torchEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Torch",
                        tint = if (torchEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Gallery button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { galleryLauncher.launch("image/*") }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = "Pick Photo",
                            tint = SoftEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Import QR Photo",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // Permission Request State
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = SoftEmerald,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Camera Permission Needed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Allow camera access to instantly scan and pair devices, or import a QR screenshot from your photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftEmerald)
                    ) {
                        Text("Grant Permission", color = Color(0xFF090A0E), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Pick Photo", color = Color.White)
                    }
                }
            }
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF8B1D1D).copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    errorMessage!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

// ── Decode helpers ─────────────────────────────────────────────────────────────
fun parseQrPairData(raw: String): ScannedPairData {
    val clean = raw.trim()
    return try {
        if (clean.startsWith("hp://pair")) {
            val uri = Uri.parse(clean)
            val ip = uri.getQueryParameter("ip")
            val altIps = uri.getQueryParameter("alt_ips")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 51234
            val id = uri.getQueryParameter("id")
            val name = uri.getQueryParameter("name")
            val secret = uri.getQueryParameter("secret") ?: clean
            val relayId = uri.getQueryParameter("relay_id") ?: id ?: secret
            val signalingUrl = uri.getQueryParameter("signaling_url")
            ScannedPairData(ip, port, id, name, secret, clean, altIps, relayId, signalingUrl)
        } else {
            // Raw code e.g. "ABC-123" or "192.168.1.10:51234"
            if (clean.contains(":")) {
                val parts = clean.split(":")
                val ip = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 51234
                ScannedPairData(ip, port, null, null, clean, clean, emptyList(), clean)
            } else {
                ScannedPairData(null, 51234, null, null, clean, clean, emptyList(), clean)
            }
        }
    } catch (e: Exception) {
        ScannedPairData(null, 51234, null, null, clean, clean, emptyList(), clean)
    }
}

private fun analyzeImageProxy(image: ImageProxy, reader: MultiFormatReader): ScannedPairData? {
    // Primary path: ImageProxy.toBitmap() handles YUV/RGB conversions and stride padding correctly across all Android devices
    try {
        val bitmap = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        val properlyRotatedBitmap = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        val result = decodeQrFromBitmap(properlyRotatedBitmap)
        if (result != null) return result
    } catch (_: Exception) {}

    // Fallback path: Direct Y-plane extraction respecting rowStride and rotation
    return try {
        decodeFromYPlane(image, reader)
    } catch (_: Exception) {
        null
    }
}

private fun decodeFromYPlane(image: ImageProxy, reader: MultiFormatReader): ScannedPairData? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    val rotation = image.imageInfo.rotationDegrees

    val yBytes = ByteArray(width * height)
    val rowBuffer = ByteArray(rowStride)
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        buffer.get(rowBuffer, 0, rowStride)
        System.arraycopy(rowBuffer, 0, yBytes, row * width, width)
    }

    val (finalData, finalWidth, finalHeight) = when (rotation) {
        90 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[(height - 1 - y) + x * height] = yBytes[x + y * width]
                }
            }
            Triple(rotated, height, width)
        }
        180 -> {
            val rotated = ByteArray(width * height)
            for (i in 0 until width * height) {
                rotated[width * height - 1 - i] = yBytes[i]
            }
            Triple(rotated, width, height)
        }
        270 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[y + (width - 1 - x) * height] = yBytes[x + y * width]
                }
            }
            Triple(rotated, height, width)
        }
        else -> Triple(yBytes, width, height)
    }

    val source = PlanarYUVLuminanceSource(
        finalData, finalWidth, finalHeight, 0, 0, finalWidth, finalHeight, false
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        val result: Result = reader.decodeWithState(binaryBitmap)
        parseQrPairData(result.text)
    } catch (_: Exception) {
        try {
            val inverted = BinaryBitmap(HybridBinarizer(source.invert()))
            val result = reader.decodeWithState(inverted)
            parseQrPairData(result.text)
        } catch (_: Exception) {
            null
        }
    } finally {
        reader.reset()
    }
}

private fun decodeQrFromBitmap(bitmap: Bitmap): ScannedPairData? {
    val intArray = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        ))
    }
    return try {
        val result: Result = reader.decodeWithState(binaryBitmap)
        parseQrPairData(result.text)
    } catch (_: Exception) {
        // Try inverted binarizer (for dark background QR)
        try {
            val inverted = BinaryBitmap(HybridBinarizer(source.invert()))
            val result = reader.decodeWithState(inverted)
            parseQrPairData(result.text)
        } catch (_: Exception) {
            null
        }
    } finally {
        reader.reset()
    }
}
