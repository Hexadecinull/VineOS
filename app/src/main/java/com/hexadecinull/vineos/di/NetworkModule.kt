package com.hexadecinull.vineos.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Get real values once the ROM store is live: see "Certificate pinning" in docs/ROM_STORE_SETUP.md for the exact openssl command; pinning is skipped below until both are filled in, since a wrong pin would break every ROM download outright
    private const val ROM_STORE_HOST = "vineos.hexadecinull.dpdns.org"
    private const val ROM_STORE_PIN_PRIMARY = "sha256/REPLACE_ME_PRIMARY_PIN"
    private const val ROM_STORE_PIN_BACKUP = "sha256/REPLACE_ME_BACKUP_PIN"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        val pinsConfigured = "REPLACE_ME" !in ROM_STORE_PIN_PRIMARY && "REPLACE_ME" !in ROM_STORE_PIN_BACKUP
        if (pinsConfigured) {
            builder.certificatePinner(
                CertificatePinner.Builder()
                    .add(ROM_STORE_HOST, ROM_STORE_PIN_PRIMARY, ROM_STORE_PIN_BACKUP)
                    .build(),
            )
        }

        return builder.build()
    }
}
