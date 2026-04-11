package com.hexadecinull.vineos.data.repository

import com.hexadecinull.vineos.data.models.ROMImage
import kotlinx.serialization.Serializable

@Serializable
data class ROMManifest(
    val version: Int = 1,
    val roms: List<ROMImage>,
)
