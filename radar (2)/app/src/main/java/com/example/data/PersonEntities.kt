package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "person_photos",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class PersonPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val imagePath: String,
    val embeddingCsv: String, // 192 float values separated by comma
    val qualityScore: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getEmbeddingArray(): FloatArray {
        if (embeddingCsv.isBlank()) return FloatArray(0)
        return try {
            embeddingCsv.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(0)
        }
    }
}

@Entity(
    tableName = "person_nicknames",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class PersonNicknameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val nickname: String,
    val announcement: String,
    val timesUsed: Int = 0,
    val lastUsedAt: Long = 0L,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
