package com.hexadecinull.vineos.di

import android.content.Context
import androidx.room.Room
import com.hexadecinull.vineos.data.repository.VineDatabase
import com.hexadecinull.vineos.data.repository.VMInstanceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VineDatabase =
        Room.databaseBuilder(
            context,
            VineDatabase::class.java,
            VineDatabase.DATABASE_NAME,
        )
        .fallbackToDestructiveMigration() // Safe for pre-1.0; replace with Migration objects post-release
        .build()

    @Provides
    fun provideVMInstanceDao(db: VineDatabase): VMInstanceDao = db.vmInstanceDao()
}
