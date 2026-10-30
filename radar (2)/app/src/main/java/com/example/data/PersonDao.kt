package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PersonWithPhotos(
    @Embedded
    val person: PersonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val photos: List<PersonPhotoEntity>
) {
    val isReadyForRecognition: Boolean
        get() = photos.any { it.getEmbeddingArray().isNotEmpty() }

    val validPhotoCount: Int
        get() = photos.count { it.getEmbeddingArray().isNotEmpty() }
}

@Dao
interface PersonDao {

    @Transaction
    @Query("SELECT * FROM persons ORDER BY createdAt DESC")
    fun getAllPersonsWithPhotos(): Flow<List<PersonWithPhotos>>

    @Transaction
    @Query("SELECT * FROM persons ORDER BY createdAt DESC")
    suspend fun getAllPersonsWithPhotosList(): List<PersonWithPhotos>

    @Transaction
    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getPersonWithPhotos(id: Long): PersonWithPhotos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PersonPhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: PersonPhotoEntity)

    @Query("DELETE FROM person_photos WHERE personId = :personId")
    suspend fun deletePhotosForPerson(personId: Long)

    // Nickname operations
    @Query("SELECT * FROM person_nicknames WHERE personId = :personId ORDER BY lastUsedAt ASC, timesUsed ASC, createdAt DESC")
    fun getNicknamesForPerson(personId: Long): Flow<List<PersonNicknameEntity>>

    @Query("SELECT * FROM person_nicknames WHERE personId = :personId ORDER BY lastUsedAt ASC, timesUsed ASC, createdAt DESC")
    suspend fun getNicknamesListForPerson(personId: Long): List<PersonNicknameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNickname(nickname: PersonNicknameEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNicknames(nicknames: List<PersonNicknameEntity>)

    @Query("UPDATE person_nicknames SET timesUsed = :timesUsed, lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateNicknameUsage(id: Long, timesUsed: Int, lastUsedAt: Long)

    @Delete
    suspend fun deleteNickname(nickname: PersonNicknameEntity)

    @Query("DELETE FROM person_nicknames WHERE personId = :personId")
    suspend fun deleteNicknamesForPerson(personId: Long)

    @Query("DELETE FROM person_nicknames")
    suspend fun clearAllNicknames()
}
