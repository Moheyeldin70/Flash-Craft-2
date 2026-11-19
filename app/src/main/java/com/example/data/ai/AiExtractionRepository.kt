package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.media.MediaStorageHelper
import com.example.domain.model.ExtractedCardDraft
import com.example.domain.model.ExtractionDepth
import com.example.domain.model.ExtractionResult
import com.example.domain.model.ExtractionStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipInputStream

private const val TAG = "AiExtractionRepo"
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Stay safely below 20MB Gemini inline limit
private const val MAX_TEXT_CHARS = 100_000

data class SelectedFileInfo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class AiExtractionRepository {

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "suggestedDeckTitle" to Schema.string(
                description = "Concise, clear title for the flashcard deck (max 60 characters)"
            ),
            "suggestedDeckDescription" to Schema.string(
                description = "Brief 1-sentence description of what this deck covers"
            ),
            "summary" to Schema.string(
                description = "A 2-3 sentence summary of the main concepts extracted from the uploaded content"
            ),
            "cards" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "front" to Schema.string(
                            description = "Prompt or question on the front of the flashcard"
                        ),
                        "back" to Schema.string(
                            description = "Clear, accurate answer on the back of the flashcard"
                        ),
                        "hint" to Schema.string(
                            description = "Short helpful clue without giving away the answer"
                        ),
                        "tags" to Schema.array(
                            items = Schema.string(),
                            description = "1 to 3 short lowercase topic tags"
                        )
                    ),
                    optionalProperties = listOf("hint", "tags")
                ),
                description = "Generated flashcards ordered from foundational concepts to details"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
                temperature = 0.35f
            }
        )
    }

    suspend fun inspectFile(context: Context, uri: Uri): SelectedFileInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = MediaStorageHelper.resolveDisplayName(context, uri) ?: "uploaded_file"
        val rawMime = resolver.getType(uri).orEmpty()
        val resolvedMime = if (rawMime.isNotBlank() && rawMime != "application/octet-stream") {
            rawMime
        } else {
            guessMimeFromName(displayName)
        }
        val sizeBytes = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        SelectedFileInfo(
            uri = uri,
            displayName = displayName,
            mimeType = resolvedMime,
            sizeBytes = sizeBytes
        )
    }

    suspend fun extractFlashcardsFromFile(
        context: Context,
        fileInfo: SelectedFileInfo,
        cardCount: Int,
        style: ExtractionStyle,
        depth: ExtractionDepth,
        customInstructions: String
    ): Result<ExtractionResult> {
        return try {
            val preparedPayload = withContext(Dispatchers.IO) {
                preparePayload(context, fileInfo)
            }

            val instructionPrompt = buildString {
                appendLine("You are an expert educator creating high-retention spaced-repetition flashcards.")
                appendLine("Analyze the uploaded file (${fileInfo.displayName}, type: ${fileInfo.mimeType}) and extract the most important knowledge into $cardCount flashcards.")
                appendLine("Card style: ${style.label} — ${style.promptHint}")
                appendLine("Detail level: ${depth.label}.")
                if (customInstructions.isNotBlank()) {
                    appendLine("User focus instructions: ${customInstructions.trim()}")
                }
                appendLine("Keep each card focused on ONE clear atomic idea so it is fast to review.")
                appendLine("Ensure front <= 400 characters and back <= 900 characters.")
            }

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(instructionPrompt)
                        when (preparedPayload) {
                            is PreparedPayload.ExtractedText -> {
                                text("Extracted file content:\n\n${preparedPayload.text}")
                            }
                            is PreparedPayload.InlineBinary -> {
                                inlineData(preparedPayload.bytes, preparedPayload.mimeType)
                            }
                        }
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))

            val parsed = parseStructuredJson(jsonText)
            if (parsed.cards.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No flashcards could be extracted from this file.")
                )
            }
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI extraction timed out", e)
            Result.failure(
                IllegalStateException("AI extraction timed out after 60 seconds. Try a smaller file or fewer cards.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI extraction failed", e)
            Result.failure(e)
        }
    }

    private fun parseStructuredJson(jsonText: String): ExtractionResult {
        val root = JSONObject(jsonText)
        val deckTitle = root.optString("suggestedDeckTitle", "AI Study Deck")
            .trim()
            .take(100)
            .ifBlank { "AI Study Deck" }
        val deckDesc = root.optString("suggestedDeckDescription", "")
            .trim()
            .take(300)
        val summary = root.optString("summary", "").trim()
        val cardsArray = root.optJSONArray("cards")

        val cards = buildList {
            if (cardsArray != null) {
                for (i in 0 until cardsArray.length()) {
                    val obj = cardsArray.optJSONObject(i) ?: continue
                    val front = obj.optString("front", "").trim().take(1000)
                    val back = obj.optString("back", "").trim().take(2000)
                    if (front.isBlank() || back.isBlank()) continue
                    val hint = obj.optString("hint", "").trim().take(300)
                    val tagsArray = obj.optJSONArray("tags")
                    val tags = buildList {
                        if (tagsArray != null) {
                            for (t in 0 until minOf(tagsArray.length(), 5)) {
                                val tag = tagsArray.optString(t, "").trim().take(40)
                                if (tag.isNotBlank()) add(tag)
                            }
                        }
                    }
                    add(
                        ExtractedCardDraft(
                            localId = UUID.randomUUID().toString(),
                            front = front,
                            back = back,
                            hint = hint,
                            tags = tags,
                            selected = true
                        )
                    )
                }
            }
        }

        return ExtractionResult(
            suggestedDeckTitle = deckTitle,
            suggestedDeckDescription = deckDesc,
            summary = summary,
            cards = cards
        )
    }

    private sealed interface PreparedPayload {
        data class ExtractedText(val text: String) : PreparedPayload
        data class InlineBinary(val bytes: ByteArray, val mimeType: String) : PreparedPayload
    }

    private fun preparePayload(context: Context, fileInfo: SelectedFileInfo): PreparedPayload {
        val rawBytes = readBytesBounded(context, fileInfo.uri)
        val lowerName = fileInfo.displayName.lowercase()
        val mime = fileInfo.mimeType.lowercase()

        // 1. Images -> compress/scale neatly as JPEG so high-res photos stay well below 20MB
        if (mime.startsWith("image/")) {
            val jpegBytes = downscaleImageToJpeg(rawBytes)
            return PreparedPayload.InlineBinary(jpegBytes, "image/jpeg")
        }

        // 2. Office OpenXML documents (.docx, .pptx, .xlsx, .odt) -> parse XML text from ZIP
        if (lowerName.endsWith(".docx") || lowerName.endsWith(".pptx") ||
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".odt") ||
            mime.contains("officedocument") || mime.contains("opendocument")
        ) {
            val officeText = extractOfficeXmlText(rawBytes)
            if (officeText.isNotBlank()) {
                return PreparedPayload.ExtractedText(officeText.take(MAX_TEXT_CHARS))
            }
        }

        // 3. Plain text / structured text / source code / markup
        if (mime.startsWith("text/") ||
            mime in setOf(
                "application/json",
                "application/xml",
                "application/csv",
                "application/javascript",
                "application/x-yaml",
                "application/rtf"
            ) ||
            isTextExtension(lowerName)
        ) {
            val text = rawBytes.toString(Charsets.UTF_8).trim()
            if (text.isBlank()) {
                throw IllegalArgumentException("The selected text file appears to be empty.")
            }
            return PreparedPayload.ExtractedText(text.take(MAX_TEXT_CHARS))
        }

        // 4. Native multimodal files (PDF, Audio, Video)
        if (mime == "application/pdf" || mime.startsWith("audio/") || mime.startsWith("video/")) {
            return PreparedPayload.InlineBinary(rawBytes, mime)
        }

        // 5. Generic unknown file fallback: check if text-readable, else send inline MIME
        if (looksLikeUtf8Text(rawBytes)) {
            val text = rawBytes.toString(Charsets.UTF_8).trim()
            if (text.isNotBlank()) {
                return PreparedPayload.ExtractedText(text.take(MAX_TEXT_CHARS))
            }
        }

        if (mime.isNotBlank() && mime != "application/octet-stream") {
            return PreparedPayload.InlineBinary(rawBytes, mime)
        }

        throw IllegalArgumentException(
            "Could not read '${fileInfo.displayName}'. Try PDF, image, audio, text, Markdown, CSV, or Office documents."
        )
    }

    private fun readBytesBounded(context: Context, uri: Uri): ByteArray {
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_INLINE_BYTES) {
                    throw IllegalArgumentException(
                        "File is larger than 18 MB. Please choose a smaller file or split it into chapters."
                    )
                }
                out.write(buf, 0, n)
            }
        } ?: throw IllegalStateException("Unable to read the selected file.")
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("The selected file is empty.")
        }
        return bytes
    }

    private fun downscaleImageToJpeg(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 2048) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return bytes
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun extractOfficeXmlText(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val factory = XmlPullParserFactory.newInstance()
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && name.endsWith(".xml") &&
                        (name.contains("document") || name.contains("slide") ||
                            name.contains("sharedstrings") || name.contains("content") ||
                            name.contains("notes"))
                    ) {
                        val parser = factory.newPullParser()
                        parser.setInput(InputStreamReader(zip, Charsets.UTF_8))
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.TEXT) {
                                val t = parser.text?.trim().orEmpty()
                                if (t.isNotBlank()) {
                                    sb.append(t).append(' ')
                                }
                            } else if (event == XmlPullParser.END_TAG &&
                                (parser.name == "p" || parser.name == "br" || parser.name == "tr")
                            ) {
                                sb.append('\n')
                            }
                            event = parser.next()
                        }
                        sb.append("\n---\n")
                        if (sb.length >= MAX_TEXT_CHARS) break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Office XML extraction failed, falling back", e)
        }
        return sb.toString().trim()
    }

    private fun looksLikeUtf8Text(bytes: ByteArray): Boolean {
        val sampleLen = minOf(bytes.size, 2048)
        var nonPrintable = 0
        for (i in 0 until sampleLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F)) {
                nonPrintable++
            }
        }
        return nonPrintable.toDouble() / sampleLen.toDouble() < 0.05
    }

    private fun isTextExtension(lowerName: String): Boolean {
        val ext = lowerName.substringAfterLast('.', "")
        return ext in setOf(
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm",
            "yaml", "yml", "tex", "org", "rst", "log", "sql", "py", "kt", "java",
            "js", "ts", "c", "cpp", "h", "swift", "go", "rs", "rb", "php", "sh"
        )
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt", "md", "markdown", "log" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }
}
