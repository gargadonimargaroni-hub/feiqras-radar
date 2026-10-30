package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.ml.FaceDetectionEngine
import com.example.ml.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

sealed class PhotoEnrollmentResult {
    data class Success(val photoId: Long, val message: String) : PhotoEnrollmentResult()
    data class Error(val reason: String) : PhotoEnrollmentResult()
}

class ProfileManager(
    private val context: Context,
    private val database: RadarDatabase,
    private val detectionEngine: FaceDetectionEngine,
    private val recognitionEngine: FaceRecognitionEngine
) {
    private val dao = database.personDao()

    val allPersonsWithPhotos: Flow<List<PersonWithPhotos>> = dao.getAllPersonsWithPhotos()

    suspend fun getAllPersonsList(): List<PersonWithPhotos> = withContext(Dispatchers.IO) {
        dao.getAllPersonsWithPhotosList()
    }

    suspend fun getPersonWithPhotos(id: Long): PersonWithPhotos? = withContext(Dispatchers.IO) {
        dao.getPersonWithPhotos(id)
    }

    suspend fun createPerson(name: String, notes: String = ""): Long = withContext(Dispatchers.IO) {
        dao.insertPerson(PersonEntity(name = name.trim(), notes = notes.trim()))
    }

    suspend fun updatePerson(person: PersonEntity) = withContext(Dispatchers.IO) {
        dao.updatePerson(person)
    }

    suspend fun deletePerson(personWithPhotos: PersonWithPhotos) = withContext(Dispatchers.IO) {
        // Delete all photo files from disk
        for (photo in personWithPhotos.photos) {
            try {
                val file = File(photo.imagePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        dao.deletePerson(personWithPhotos.person)
    }

    suspend fun deletePhoto(photo: PersonPhotoEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(photo.imagePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        dao.deletePhoto(photo)
    }

    fun getNicknamesForPerson(personId: Long): Flow<List<PersonNicknameEntity>> =
        dao.getNicknamesForPerson(personId)

    suspend fun deleteNickname(nickname: PersonNicknameEntity) = withContext(Dispatchers.IO) {
        dao.deleteNickname(nickname)
    }

    suspend fun deleteNicknamesForPerson(personId: Long) = withContext(Dispatchers.IO) {
        dao.deleteNicknamesForPerson(personId)
    }

    /**
     * Enrolls a photo for a person:
     * 1. Loads and corrects orientation
     * 2. Detects faces with ML Kit
     * 3. Rejects if no face is found
     * 4. Extracts MobileFaceNet 192-d embedding
     * 5. Saves face image locally and stores in Room database
     */
    suspend fun enrollPhotoFromUri(personId: Long, uri: Uri): PhotoEnrollmentResult =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadOrientedBitmap(context, uri)
                    ?: return@withContext PhotoEnrollmentResult.Error("Could not load image file.")

                enrollBitmap(personId, bitmap)
            } catch (e: Exception) {
                PhotoEnrollmentResult.Error(e.localizedMessage ?: "Unknown enrollment error")
            }
        }

    suspend fun enrollBitmap(personId: Long, bitmap: Bitmap): PhotoEnrollmentResult =
        withContext(Dispatchers.IO) {
            try {
                // 1. Face detection
                val faces = detectionEngine.detectFaces(bitmap)
                if (faces.isEmpty()) {
                    return@withContext PhotoEnrollmentResult.Error(
                        "No face detected in this photo. Please select a clear, well-lit photo showing the person's face."
                    )
                }

                // If multiple faces, pick the largest one (primary target)
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!

                // 2. Face embedding extraction
                val embedding = recognitionEngine.extractEmbedding(bitmap, primaryFace.boundingBox)
                    ?: return@withContext PhotoEnrollmentResult.Error(
                        "Failed to generate face embedding. Please try another photo."
                    )

                // 3. Save photo locally
                val profilesDir = File(context.filesDir, "profiles").apply { mkdirs() }
                val photoFile = File(profilesDir, "person_${personId}_${UUID.randomUUID()}.jpg")

                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                // 4. Save to Room database
                val embeddingCsv = embedding.joinToString(",") { it.toString() }
                val photoEntity = PersonPhotoEntity(
                    personId = personId,
                    imagePath = photoFile.absolutePath,
                    embeddingCsv = embeddingCsv,
                    qualityScore = 1.0f
                )

                val photoId = dao.insertPhoto(photoEntity)
                val msg = if (faces.size > 1) {
                    "Face registered successfully (${faces.size} faces found, used primary face)."
                } else {
                    "Face registered successfully."
                }
                PhotoEnrollmentResult.Success(photoId, msg)
            } catch (e: Exception) {
                PhotoEnrollmentResult.Error("Enrollment error: ${e.message}")
            }
        }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        var input: InputStream? = null
        return try {
            input = context.contentResolver.openInputStream(uri) ?: return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, boundsOptions)
            input.close()

            // Calculate sample size to avoid OOM
            var inSampleSize = 1
            val maxDimension = 1280
            while (boundsOptions.outWidth / inSampleSize > maxDimension || boundsOptions.outHeight / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            input = context.contentResolver.openInputStream(uri) ?: return null
            val rawBitmap = BitmapFactory.decodeStream(input, null, decodeOptions) ?: return null
            input.close()

            // Check EXIF orientation
            val exifInput = context.contentResolver.openInputStream(uri)
            val orientation = exifInput?.use {
                val exif = ExifInterface(it)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(rawBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(rawBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(rawBitmap, 270f)
                else -> rawBitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
