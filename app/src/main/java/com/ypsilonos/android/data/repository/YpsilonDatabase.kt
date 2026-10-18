package com.ypsilonos.android.data.repository

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Migration
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.SupportSQLiteDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.ypsilonos.android.data.models.VMInstance
import com.ypsilonos.android.data.models.VMStatus
import kotlinx.coroutines.flow.Flow

class YpsilonConverters {
    @TypeConverter
    fun fromVMStatus(status: VMStatus): String = status.name

    @TypeConverter
    fun toVMStatus(value: String): VMStatus = runCatching { VMStatus.valueOf(value) }.getOrDefault(VMStatus.STOPPED)
}

// Migration from version 1 to 2 adds YpsilonOS compatibility columns.
// Migration from 2 to 3 adds safety / lifecycle columns.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN gameMode TEXT NOT NULL DEFAULT 'BALANCED'")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN buildPropsJson TEXT NOT NULL DEFAULT '{}'")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN rootHidden INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN selinuxEnforcing INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN renderScale REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN autoSuspend INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN networkIsolated INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE vm_instances ADD COLUMN lastBackupAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Dao
interface VMInstanceDao {
    @Query("SELECT * FROM vm_instances ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<VMInstance>>

    @Query("SELECT * FROM vm_instances WHERE id = :id")
    suspend fun getById(id: String): VMInstance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: VMInstance)

    @Update
    suspend fun update(instance: VMInstance)

    @Query("UPDATE vm_instances SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: VMStatus)

    @Query("UPDATE vm_instances SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(instance: VMInstance)

    @Query("DELETE FROM vm_instances WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM vm_instances")
    suspend fun count(): Int
}

@Database(
    entities = [VMInstance::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(YpsilonConverters::class)
abstract class YpsilonDatabase : RoomDatabase() {
    abstract fun vmInstanceDao(): VMInstanceDao

    companion object {
        const val DATABASE_NAME = "ypsilonos.db"

        @Volatile
        private var INSTANCE: YpsilonDatabase? = null

        fun getInstance(context: Context): YpsilonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YpsilonDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
