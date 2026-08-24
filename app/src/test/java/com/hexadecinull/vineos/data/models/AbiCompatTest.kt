package com.hexadecinull.vineos.data.models

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AbiCompatTest {
    private fun rom(vararg abis: String) = ROMImage(
        id = "test-rom",
        displayName = "Test ROM",
        androidVersion = "7.1.2",
        apiLevel = 25,
        description = "",
        downloadUrl = "https://example.com/test.vrom",
        sha256 = "0".repeat(64),
        sizeBytes = 1_000_000L,
        supportedAbis = abis.toList(),
        has32BitSupport = true,
        releaseDate = "2026-01-01",
    )

    // arm64-v8a host

    @Test
    fun `arm64 host runs arm64 guest natively`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `arm64 host runs armeabi-v7a guest natively`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARMEABI_V7A, listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `arm64 host runs x86_64 guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86_64, listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    @Test
    fun `arm64 host runs x86 guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86, listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    // x86_64 host

    @Test
    fun `x86_64 host runs x86_64 guest natively`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86_64, listOf(AbiCompat.X86_64))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `x86_64 host runs x86 guest natively via 32-bit compat layer`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86, listOf(AbiCompat.X86_64))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `x86_64 host runs arm64 guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, listOf(AbiCompat.X86_64))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    @Test
    fun `x86_64 host runs armeabi-v7a guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARMEABI_V7A, listOf(AbiCompat.X86_64))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    // armeabi-v7a host (32-bit)

    @Test
    fun `armeabi-v7a host runs armeabi-v7a guest natively`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARMEABI_V7A, listOf(AbiCompat.ARMEABI_V7A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `armeabi-v7a host cannot run arm64 guest`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, listOf(AbiCompat.ARMEABI_V7A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `armeabi-v7a host cannot run x86_64 guest`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86_64, listOf(AbiCompat.ARMEABI_V7A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `armeabi-v7a host runs x86 guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86, listOf(AbiCompat.ARMEABI_V7A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    // x86 host (32-bit)

    @Test
    fun `x86 host runs x86 guest natively`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86, listOf(AbiCompat.X86))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `x86 host cannot run x86_64 guest`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.X86_64, listOf(AbiCompat.X86))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `x86 host runs arm64 guest via qemu`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, listOf(AbiCompat.X86))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    // armeabi host (legacy 32-bit only, not shipped as a real host ABI filter)

    @Test
    fun `armeabi host only runs armeabi guest`() {
        assertThat(AbiCompat.hostCanRun(AbiCompat.ARMEABI, listOf(AbiCompat.ARMEABI)))
            .isEqualTo(AbiCompat.RunMode.NATIVE)
        assertThat(AbiCompat.hostCanRun(AbiCompat.ARMEABI_V7A, listOf(AbiCompat.ARMEABI)))
            .isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    // Edge cases

    @Test
    fun `unknown guest abi is unavailable`() {
        val mode = AbiCompat.hostCanRun("mips", listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `unknown host abi is unavailable`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, listOf("mips"))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `empty host abi list is unavailable`() {
        val mode = AbiCompat.hostCanRun(AbiCompat.ARM64_V8A, emptyList())
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `only the primary host abi is consulted`() {
        // A secondary ABI in the host list shouldn't change the result; Build.SUPPORTED_ABIS is ordered by preference, so only [0] counts
        val mode = AbiCompat.hostCanRun(
            AbiCompat.ARM64_V8A,
            listOf(AbiCompat.ARMEABI_V7A, AbiCompat.ARM64_V8A),
        )
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    // romRunMode: picks the best available mode across a ROM's supported ABIs

    @Test
    fun `romRunMode picks native when any supported abi is native`() {
        val mode = AbiCompat.romRunMode(
            rom(AbiCompat.X86_64, AbiCompat.ARM64_V8A),
            listOf(AbiCompat.ARM64_V8A),
        )
        assertThat(mode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `romRunMode falls back to qemu when nothing runs natively`() {
        val mode = AbiCompat.romRunMode(
            rom(AbiCompat.X86_64, AbiCompat.X86),
            listOf(AbiCompat.ARM64_V8A),
        )
        assertThat(mode).isEqualTo(AbiCompat.RunMode.QEMU)
    }

    @Test
    fun `romRunMode is unavailable only when every supported abi is unavailable`() {
        val mode = AbiCompat.romRunMode(
            rom(AbiCompat.ARM64_V8A, AbiCompat.X86_64),
            listOf(AbiCompat.ARMEABI_V7A),
        )
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `romRunMode with no supported abis is unavailable`() {
        val mode = AbiCompat.romRunMode(rom(), listOf(AbiCompat.ARM64_V8A))
        assertThat(mode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `ALL_EMULATED_ABIS contains exactly the five known abis`() {
        assertThat(AbiCompat.ALL_EMULATED_ABIS).containsExactly(
            AbiCompat.ARM64_V8A,
            AbiCompat.ARMEABI_V7A,
            AbiCompat.ARMEABI,
            AbiCompat.X86_64,
            AbiCompat.X86,
        )
    }
}
