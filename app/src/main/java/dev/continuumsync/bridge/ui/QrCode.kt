package dev.continuumsync.bridge.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.continuumsync.bridge.ui.theme.TextLow

@Composable
fun QrCodePanel(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 218.dp
) {
    val image = remember(payload, size) {
        runCatching { createQrBitmap(payload, 720) }.getOrNull()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "ContinuumSync pairing QR code",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Text(
                    "QR code unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow
                )
            }
        }
    }
}

private fun createQrBitmap(content: String, size: Int): androidx.compose.ui.graphics.ImageBitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        hints
    )
    val pixels = IntArray(size * size)
    val foreground = android.graphics.Color.rgb(79, 70, 229)
    val background = android.graphics.Color.WHITE
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix[x, y]) foreground else background
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
