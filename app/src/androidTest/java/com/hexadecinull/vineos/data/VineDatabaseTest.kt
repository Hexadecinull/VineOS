package com.hexadecinull.vineos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.VineDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VineDatabaseTest {
    private lateinit var db: VineDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VineDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() = db.close()

    @Test
    fun insertAndReadInstance() = runTest {
        val instance = buildInstance("db-test-1")
        db.vmInstanceDao().insert(instance)
        val fetched = db.vmInstanceDao().getById("db-test-1")
        assertThat(fetched).isNotNull()
        assertThat(fetched!!.name).isEqualTo("DB Test Instance")
    }

    @Test
    fun observeAllReturnsAllInstances() = runTest {
        db.vmInstanceDao().insert(buildInstance("id-1", name = "Instance A"))
        db.vmInstanceDao().insert(buildInstance("id-2", name = "Instance B"))
        val all = db.vmInstanceDao().observeAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun observeAllOrderedByLastUsedDescending() = runTest {
        db.vmInstanceDao().insert(buildInstance("old", lastUsedAt = 1000L))
        db.vmInstanceDao().insert(buildInstance("new", lastUsedAt = 2000L))
        val all = db.vmInstanceDao().observeAll().first()
        assertThat(all.first().id).isEqualTo("new")
    }

    @Test
    fun updateStatusChangesStatusCorrectly() = runTest {
        db.vmInstanceDao().insert(buildInstance("status-test"))
        db.vmInstanceDao().updateStatus("status-test", VMStatus.RUNNING)
        val fetched = db.vmInstanceDao().getById("status-test")
        assertThat(fetched!!.status).isEqualTo(VMStatus.RUNNING)
    }

    @Test
    fun deleteInstanceRemovesFromDb() = runTest {
        val instance = buildInstance("del-test")
        db.vmInstanceDao().insert(instance)
        db.vmInstanceDao().delete(instance)
        val fetched = db.vmInstanceDao().getById("del-test")
        assertThat(fetched).isNull()
    }

    @Test
    fun insertWithSameIdReplacesExisting() = runTest {
        db.vmInstanceDao().insert(buildInstance("replace-test", name = "Original"))
        db.vmInstanceDao().insert(buildInstance("replace-test", name = "Replaced"))
        val fetched = db.vmInstanceDao().getById("replace-test")
        assertThat(fetched!!.name).isEqualTo("Replaced")
    }

    @Test
    fun countReturnsCorrectNumber() = runTest {
        assertThat(db.vmInstanceDao().count()).isEqualTo(0)
        db.vmInstanceDao().insert(buildInstance("c1"))
        db.vmInstanceDao().insert(buildInstance("c2"))
        assertThat(db.vmInstanceDao().count()).isEqualTo(2)
    }

    private fun buildInstance(
        id: String,
        name: String = "DB Test Instance",
        lastUsedAt: Long = System.currentTimeMillis(),
    ) = VMInstance(
        id = id,
        name = name,
        romId = "vine-rom-7",
        romVersion = "7.1.2",
        storagePath = "/data/instances/$id",
        androidVersionDisplay = "Android 7.1 Nougat",
        lastUsedAt = lastUsedAt,
    )
}
