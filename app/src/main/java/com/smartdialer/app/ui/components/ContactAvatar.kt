package com.smartdialer.app.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance In-Memory LRU Cache for decoded Contact Photos.
 * Ensures butter-smooth 60fps scrolling without repeated ContentResolver IPC.
 */
object ContactPhotoLoader {
    private val memoryCache = object : LruCache<String, Bitmap>(200) {}

    suspend fun loadPhoto(
        context: Context,
        photoUri: String?,
        contactId: String?,
        preferHighRes: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cleanPhotoUri = photoUri?.trim()?.ifBlank { null }
        val cleanContactId = contactId?.trim()?.ifBlank { null }

        if (cleanPhotoUri == null && cleanContactId == null) {
            return@withContext null
        }

        val cacheKey = "${cleanPhotoUri.orEmpty()}#${cleanContactId.orEmpty()}#$preferHighRes"
        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { return@withContext it }
        }

        var bitmap: Bitmap? = null

        // 1. Try directly reading from photoUri
        if (cleanPhotoUri != null) {
            try {
                val parsed = Uri.parse(cleanPhotoUri)
                context.contentResolver.openInputStream(parsed)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                // Fallback to contact ID
            }
        }

        // 2. Try official ContactsContract.Contacts.openContactPhotoInputStream (Google & Device contacts)
        if (bitmap == null && cleanContactId != null) {
            try {
                val cId = cleanContactId.toLongOrNull()
                if (cId != null && cId > 0) {
                    val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cId)
                    ContactsContract.Contacts.openContactPhotoInputStream(
                        context.contentResolver,
                        contactUri,
                        preferHighRes
                    )?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (bitmap != null) {
            synchronized(memoryCache) {
                memoryCache.put(cacheKey, bitmap)
            }
        }

        bitmap
    }

    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }
}

/**
 * Reusable Contact Avatar Composable.
 * If the contact has a profile picture synced from Google or device storage,
 * it renders the high-quality circular photo. Otherwise, it gracefully falls back
 * to a styled pastel circular background with the contact's initial letter.
 */
@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    contactId: String? = null,
    size: Dp = 44.dp,
    preferHighRes: Boolean = false,
    fontSize: TextUnit = 17.sp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(photoUri, contactId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoUri, contactId) {
        val loaded = ContactPhotoLoader.loadPhoto(context, photoUri, contactId, preferHighRes)
        bitmap = loaded
    }

    val avatarColors = listOf(
        Color(0xFFDBEAFE) to Color(0xFF1D4ED8), // Blue
        Color(0xFFFEF3C7) to Color(0xFFB45309), // Amber
        Color(0xFFDCFCE7) to Color(0xFF15803D), // Green
        Color(0xFFEDE9FE) to Color(0xFF6D28D9), // Purple
        Color(0xFFFFE4E6) to Color(0xFFBE123C)  // Pink
    )
    val colorPair = avatarColors[Math.abs(name.hashCode()) % avatarColors.size]
    val initial = name.trim().firstOrNull()?.uppercase() ?: "#"

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colorPair.first),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = colorPair.second
            )
        }
    }
}
