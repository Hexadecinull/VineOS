package com.hexadecinull.vineos.data.models

import android.os.Build

object AbiCompat {
    const val ARM64_V8A   = "arm64-v8a"
    const val ARMEABI_V7A = "armeabi-v7a"
    const val ARMEABI     = "armeabi"
    const val X86_64      = "x86_64"
    const val X86         = "x86"

    val ALL_EMULATED_ABIS = listOf(ARM64_V8A, ARMEABI_V7A, ARMEABI, X86_64, X86)

    enum class RunMode { NATIVE, QEMU, UNAVAILABLE }

    fun hostCanRun(guestAbi: String, hostAbis: List<String> = Build.SUPPORTED_ABIS.toList()): RunMode {
        val primary = hostAbis.firstOrNull() ?: return RunMode.UNAVAILABLE
        return when (primary) {
            X86_64 -> when (guestAbi) {
                X86_64      -> RunMode.NATIVE
                X86         -> RunMode.NATIVE   // 32-bit compat layer
                ARM64_V8A   -> RunMode.QEMU
                ARMEABI_V7A -> RunMode.QEMU
                ARMEABI     -> RunMode.QEMU
                else        -> RunMode.UNAVAILABLE
            }
            X86 -> when (guestAbi) {
                X86         -> RunMode.NATIVE
                X86_64      -> RunMode.UNAVAILABLE  // 32-bit host can't execute 64-bit guests
                ARM64_V8A   -> RunMode.QEMU
                ARMEABI_V7A -> RunMode.QEMU
                ARMEABI     -> RunMode.QEMU
                else        -> RunMode.UNAVAILABLE
            }
            ARM64_V8A -> when (guestAbi) {
                ARM64_V8A   -> RunMode.NATIVE
                ARMEABI_V7A -> RunMode.NATIVE   // native AArch32 or QEMU fallback, both supported
                ARMEABI     -> RunMode.NATIVE
                X86         -> RunMode.QEMU
                X86_64      -> RunMode.QEMU
                else        -> RunMode.UNAVAILABLE
            }
            ARMEABI_V7A -> when (guestAbi) {
                ARMEABI_V7A -> RunMode.NATIVE
                ARMEABI     -> RunMode.NATIVE
                ARM64_V8A   -> RunMode.UNAVAILABLE  // 32-bit host can't execute 64-bit guests
                X86_64      -> RunMode.UNAVAILABLE
                X86         -> RunMode.QEMU
                else        -> RunMode.UNAVAILABLE
            }
            ARMEABI -> when (guestAbi) {
                ARMEABI     -> RunMode.NATIVE
                else        -> RunMode.UNAVAILABLE
            }
            else -> RunMode.UNAVAILABLE
        }
    }

    fun romRunMode(rom: ROMImage, hostAbis: List<String> = Build.SUPPORTED_ABIS.toList()): RunMode = rom.supportedAbis
            .map { hostCanRun(it, hostAbis) }
            .minByOrNull { it.ordinal }
            ?: RunMode.UNAVAILABLE
}
