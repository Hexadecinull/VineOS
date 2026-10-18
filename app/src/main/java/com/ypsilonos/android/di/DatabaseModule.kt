package com.ypsilonos.android.di

import android.content.Context
import com.ypsilonos.android.data.repository.VMInstanceDao
import com.ypsilonos.android.data.repository.YpsilonDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): YpsilonDatabase =
        YpsilonDatabase.getInstance(context)

    @Provides
    fun provideVMInstanceDao(db: YpsilonDatabase): VMInstanceDao = db.vmInstanceDao()
}
