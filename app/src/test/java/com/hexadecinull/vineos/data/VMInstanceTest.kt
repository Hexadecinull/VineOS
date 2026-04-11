package com.hexadecinull.vineos.data

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.VineConverters
import org.junit.Test

class VMInstanceTest {

    private val converters = VineConverters()

    // ── VMStatus converter round-trips ────────────────────────────────────────

    @Test
    fun `VMStatus STOPPED converts to string and back`() {
        val string = converters.fromVMStatus(VMStatus.STOPPED)
        val result = converters.toVMStatus(string)
        assertThat(result).isEqualTo(VMStatus.STOPPED)
    }

    @Test
    fun `VMStatus RUNNING converts to string and back`() {
        val string = converters.fromVMStatus(VMStatus.RUNNING)
        val result = converters.toVMStatus(string)
        assertThat(result).isEqualTo(VMStatus.RUNNING)
    }

    @Test
    fun `VMStatus BOOTING converts to string and back`() {
        val result = converters.toVMStatus(converters.fromVMStatus(VMStatus.BOOTING))
        assertThat(result).isEqualTo(VMStatus.BOOTING)
    }

    @Test
    fun `toVMStatus with unknown string defaults to STOPPED`() {
        val result = converters.toVMStatus("COMPLETELY_UNKNOWN_VALUE")
        assertThat(result).isEqualTo(VMStatus.STOPPED)
    }

    @Test
    fun `all VMStatus values round-trip correctly`() {
        VMStatus.entries.forEach { status ->
            val string = converters.fromVMStatus(status)
            val back = converters.toVMStatus(string)
            assertThat(back)
                .named("round-trip for $status")
                .isEqualTo(status)
        }
    }

    // ── VMInstance defaults ───────────────────────────────────────────────────

    @Test
    fun `VMInstance has STOPPED as default status`() {
        val instance = buildInstance()
        assertThat(instance.status).isEqualTo(VMStatus.STOPPED)
    }

    @Test
    fun `VMInstance id is non-empty by default`() {
        val instance = buildInstance()
        assertThat(instance.id).isNotEmpty()
    }

    @Test
    fun `two VMInstances created without explicit id have different ids`() {
        val a = buildInstance(name = "A")
        val b = buildInstance(name = "B")
        assertThat(a.id).isNotEqualTo(b.id)
    }

    @Test
    fun `VMInstance createdAt and lastUsedAt are populated`() {
        val before = System.currentTimeMillis()
        val instance = buildInstance()
        val after = System.currentTimeMillis()

        assertThat(instance.createdAt).isAtLeast(before)
        assertThat(instance.createdAt).isAtMost(after)
        assertThat(instance.lastUsedAt).isAtLeast(before)
    }

    @Test
    fun `VMInstance isRooted defaults to false`() {
        val instance = buildInstance()
        assertThat(instance.isRooted).isFalse()
    }

    @Test
    fun `VMInstance with explicit id preserves it`() {
        val instance = buildInstance(id = "fixed-test-id")
        assertThat(instance.id).isEqualTo("fixed-test-id")
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildInstance(
        id: String = java.util.UUID.randomUUID().toString(),
        name: String = "Test Instance",
    ) = VMInstance(
        id = id,
        name = name,
        romId = "vine-rom-7",
        romVersion = "7.1.2",
        storagePath = "/data/data/com.hexadecinull.vineos/files/instances/$id",
        androidVersionDisplay = "Android 7.1 Nougat",
    )
}
