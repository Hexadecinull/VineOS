package com.ypsilonos.android.data.repository

import com.ypsilonos.android.data.models.ROMImage
import kotlinx.serialization.Serializable

@Serializable
data class ROMManifest(val version: Int = 1, val roms: List<ROMImage>)
