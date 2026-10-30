package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alert.RadarAlertManager
import com.example.data.PersonEntity
import com.example.data.PersonNicknameEntity
import com.example.data.PersonPhotoEntity
import com.example.data.PersonWithPhotos
import com.example.data.RadarDatabase
import com.example.ml.FaceRecognitionEngine
import com.example.ml.HumorCommentaryEngine
import com.example.ml.HumorLevel
import com.example.ml.HumorSettings
import com.example.ml.RadarStatus
import com.example.ml.RecognitionCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadarRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: RadarDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAppNameIsRadar() {
        val appName = context.getString(R.string.app_name)
        assertEquals("RADAR", appName)
    }

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val emb1 = FloatArray(192) { 1f / sqrt(192f) }
        val emb2 = FloatArray(192) { 1f / sqrt(192f) }

        val sim = FaceRecognitionEngine.cosineSimilarity(emb1, emb2)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityDifferentVectors() {
        val emb1 = FloatArray(192) { 0f }.apply { this[0] = 1f }
        val emb2 = FloatArray(192) { 0f }.apply { this[1] = 1f }

        val sim = FaceRecognitionEngine.cosineSimilarity(emb1, emb2)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun testPersonAndPhotoDatabasePersistence() = runBlocking {
        val dao = db.personDao()

        // 1. Insert Person
        val personId = dao.insertPerson(PersonEntity(name = "Agent John", notes = "Priority 1"))
        assertTrue(personId > 0)

        // 2. Insert Photo with 192-d embedding
        val fakeEmb = FloatArray(192) { 0.5f }
        val embCsv = fakeEmb.joinToString(",")
        val photoId = dao.insertPhoto(
            PersonPhotoEntity(
                personId = personId,
                imagePath = "/data/local/tmp/face1.jpg",
                embeddingCsv = embCsv
            )
        )
        assertTrue(photoId > 0)

        // 3. Query PersonWithPhotos
        val personWithPhotos = dao.getPersonWithPhotos(personId)
        assertNotNull(personWithPhotos)
        assertEquals("Agent John", personWithPhotos!!.person.name)
        assertEquals(1, personWithPhotos.photos.size)
        assertTrue(personWithPhotos.isReadyForRecognition)
        assertEquals(192, personWithPhotos.photos[0].getEmbeddingArray().size)

        // 4. Delete Person and check cascade
        dao.deletePerson(personWithPhotos.person)
        val deleted = dao.getPersonWithPhotos(personId)
        assertEquals(null, deleted)
    }

    @Test
    fun testDifferentProfilesReceiveDistinctNicknames() = runBlocking {
        val humorEngine = HumorCommentaryEngine(context, db)
        val dao = db.personDao()

        val p1 = dao.insertPerson(PersonEntity(name = "Person Alpha"))
        val p2 = dao.insertPerson(PersonEntity(name = "Person Beta"))

        val commentary1 = humorEngine.getNextCommentaryForPerson(p1, "Person Alpha")
        val commentary2 = humorEngine.getNextCommentaryForPerson(p2, "Person Beta")

        assertNotNull(commentary1)
        assertNotNull(commentary2)
        assertTrue(commentary1.announcement.isNotBlank())
        assertTrue(commentary2.announcement.isNotBlank())

        val p1Nicknames = dao.getNicknamesListForPerson(p1)
        val p2Nicknames = dao.getNicknamesListForPerson(p2)

        assertTrue(p1Nicknames.isNotEmpty())
        assertTrue(p2Nicknames.isNotEmpty())

        // The collections must be seeded distinctly
        val titles1 = p1Nicknames.map { it.nickname }
        val titles2 = p2Nicknames.map { it.nickname }
        assertNotEquals(titles1, titles2)
    }

    @Test
    fun testCommentaryAvoidsImmediateRepetition() = runBlocking {
        val humorEngine = HumorCommentaryEngine(context, db)
        val dao = db.personDao()
        val personId = dao.insertPerson(PersonEntity(name = "Alex"))

        val first = humorEngine.getNextCommentaryForPerson(personId, "Alex")
        val second = humorEngine.getNextCommentaryForPerson(personId, "Alex")

        assertNotNull(first)
        assertNotNull(second)
        // With multiple nicknames in the generated pool, it must rotate and not repeat the exact same one back-to-back
        assertNotEquals(first.nickname, second.nickname)
    }

    @Test
    fun testHumorLevelSettings() = runBlocking {
        val humorEngine = HumorCommentaryEngine(context, db)
        assertEquals(HumorLevel.EXTRA_SILLY, humorEngine.settings.value.humorLevel)

        humorEngine.setHumorLevel(HumorLevel.MILD)
        assertEquals(HumorLevel.MILD, humorEngine.settings.value.humorLevel)

        humorEngine.setIntervalSeconds(15)
        assertEquals(15, humorEngine.settings.value.intervalSeconds)
    }

    @Test
    fun testAlertManagerContinuousAlarmStartAndStop() {
        val humorEngine = HumorCommentaryEngine(context, db)
        val alertManager = RadarAlertManager(context, humorEngine)
        assertFalse(alertManager.isAlarmActive.value)

        alertManager.startContinuousAlert(1L, "Target Alice")
        assertTrue(alertManager.isAlarmActive.value)

        alertManager.stopContinuousAlert()
        assertFalse(alertManager.isAlarmActive.value)
    }

    @Test
    fun testAlertManagerMuteWhileAlarmActive() {
        val humorEngine = HumorCommentaryEngine(context, db)
        val alertManager = RadarAlertManager(context, humorEngine)
        alertManager.startContinuousAlert(1L, "Target Alice")
        assertTrue(alertManager.isAlarmActive.value)

        // Mute silences audio but keeps alarm active for recognition
        alertManager.setMuted(true)
        assertTrue(alertManager.isMuted.value)
        assertTrue(alertManager.isAlarmActive.value)

        // Unmuting restores alert
        alertManager.setMuted(false)
        assertFalse(alertManager.isMuted.value)
        assertTrue(alertManager.isAlarmActive.value)

        alertManager.stopContinuousAlert()
        assertFalse(alertManager.isAlarmActive.value)
    }

    @Test
    fun testRecognitionCoordinatorLifecycle() {
        val humorEngine = HumorCommentaryEngine(context, db)
        val alertManager = RadarAlertManager(context, humorEngine)
        val engine = FaceRecognitionEngine(context)
        val coordinator = RecognitionCoordinator(engine, alertManager)

        assertEquals(RadarStatus.Ready, coordinator.status.value)

        coordinator.startMonitoring()
        assertEquals(RadarStatus.Scanning, coordinator.status.value)

        coordinator.stopMonitoring()
        assertEquals(RadarStatus.Ready, coordinator.status.value)
    }
}
