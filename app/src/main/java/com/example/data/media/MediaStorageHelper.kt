package com.example.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SavedMediaAsset(
    val uriString: String,
    val mediaType: MediaType,
    val displayName: String
)

object MediaStorageHelper {
    private const val MEDIA_DIR = "card_media"
    private const val MAX_MEDIA_BYTES = 25L * 1024L * 1024L

    suspend fun importMediaFromUri(
        context: Context,
        sourceUri: Uri,
        expectedType: MediaType
    ): Result<SavedMediaAsset> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val displayName = resolveDisplayName(context, sourceUri) ?: when (expectedType) {
                MediaType.IMAGE -> "image_${System.currentTimeMillis()}.jpg"
                MediaType.AUDIO -> "audio_${System.currentTimeMillis()}.m4a"
                MediaType.NONE -> "media_${System.currentTimeMillis()}"
            }

            val mimeType = resolver.getType(sourceUri).orEmpty()
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                ?.takeIf { it.isNotBlank() }
                ?: displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                ?: if (expectedType == MediaType.IMAGE) "jpg" else "m4a"

            val dir = File(context.filesDir, MEDIA_DIR).apply { mkdirs() }
            val destFile = File(dir, "${UUID.randomUUID()}.$extension")

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_MEDIA_BYTES) {
                            destFile.delete()
                            return@withContext Result.failure(
                                IllegalArgumentException("Media file exceeds 25 MB limit.")
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext Result.failure(
                IllegalStateException("Could not open selected media file.")
            )

            val uriString = Uri.fromFile(destFile).toString()
            Result.success(
                SavedMediaAsset(
                    uriString = uriString,
                    mediaType = expectedType,
                    displayName = displayName.take(120)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
