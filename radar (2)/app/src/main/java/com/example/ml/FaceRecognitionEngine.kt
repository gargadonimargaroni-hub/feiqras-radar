package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceRecognitionEngine(private val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        private const val TAG = "FaceRecognitionEngine"
        private const val MODEL_FILENAME = "mobilefacenet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_DIM = 192

        fun cosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
            if (emb1.isEmpty() || emb2.isEmpty() || emb1.size != emb2.size) return 0f
            var dot = 0f
            for (i in emb1.indices) {
                dot += emb1[i] * emb2[i]
            }
            return dot.coerceIn(-1.0f, 1.0f)
        }
    }

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val fileDescriptor = context.assets.openFd(MODEL_FILENAME)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "MobileFaceNet loaded successfully from assets.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load MobileFaceNet model", e)
        }
    }

    val isModelLoaded: Boolean
        get() = interpreter != null

    suspend fun extractEmbedding(bitmap: Bitmap, faceBoundingBox: Rect? = null): FloatArray? =
        withContext(Dispatchers.Default) {
            val interp = interpreter ?: return@withContext null
            try {
                // 1. Crop face region with 10% safety margin
                val croppedFace = cropFace(bitmap, faceBoundingBox)
                // 2. Resize to 112x112
                val scaledBitmap = if (croppedFace.width == INPUT_SIZE && croppedFace.height == INPUT_SIZE) {
                    croppedFace
                } else {
                    Bitmap.createScaledBitmap(croppedFace, INPUT_SIZE, INPUT_SIZE, true)
                }

                // 3. Preprocess to ByteBuffer [-1.0f, 1.0f]
                val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }

                val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
                scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

                var pixelIndex = 0
                for (i in 0 until INPUT_SIZE) {
                    for (j in 0 until INPUT_SIZE) {
                        val pixel = pixels[pixelIndex++]
                        // MobileFaceNet standard normalization: (pixel - 128) / 128
                        val r = (((pixel shr 16) and 0xFF) - 128f) / 128f
                        val g = (((pixel shr 8) and 0xFF) - 128f) / 128f
                        val b = ((pixel and 0xFF) - 128f) / 128f
                        inputBuffer.putFloat(r)
                        inputBuffer.putFloat(g)
                        inputBuffer.putFloat(b)
                    }
                }

                if (croppedFace != bitmap && croppedFace != scaledBitmap) {
                    croppedFace.recycle()
                }
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }

                // 4. Run inference
                val output = Array(1) { FloatArray(EMBEDDING_DIM) }
                interp.run(inputBuffer, output)

                // 5. L2 Normalize embedding vector
                val rawEmbedding = output[0]
                var sumSq = 0f
                for (v in rawEmbedding) {
                    sumSq += v * v
                }
                val norm = sqrt(sumSq.toDouble()).toFloat().coerceAtLeast(1e-10f)
                val normalizedEmbedding = FloatArray(EMBEDDING_DIM)
                for (k in rawEmbedding.indices) {
                    normalizedEmbedding[k] = rawEmbedding[k] / norm
                }

                normalizedEmbedding
            } catch (e: Exception) {
                Log.e(TAG, "Embedding extraction failed", e)
                null
            }
        }

    private fun cropFace(src: Bitmap, box: Rect?): Bitmap {
        if (box == null) return src
        val marginX = (box.width() * 0.12f).toInt()
        val marginY = (box.height() * 0.12f).toInt()

        val left = max(0, box.left - marginX)
        val top = max(0, box.top - marginY)
        val right = min(src.width, box.right + marginX)
        val bottom = min(src.height, box.bottom + marginY)

        val width = right - left
        val height = bottom - top

        return if (width > 10 && height > 10) {
            Bitmap.createBitmap(src, left, top, width, height)
        } else {
            src
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter", e)
        }
    }
}
