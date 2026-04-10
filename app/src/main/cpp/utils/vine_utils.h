#pragma once

#include <string>
#include <vector>
#include <optional>
#include <sys/types.h>

namespace vine {

// ─── Filesystem helpers ───────────────────────────────────────────────────────

/**
 * Recursively create a directory path (like mkdir -p).
 * Returns true on success or if the path already exists.
 */
bool mkdirs(const std::string& path, mode_t mode = 0755);

/**
 * Check if a path exists.
 */
bool path_exists(const std::string& path);

/**
 * Check if a path is a directory.
 */
bool is_directory(const std::string& path);

/**
 * Read entire file contents into a string.
 * Returns nullopt on failure.
 */
std::optional<std::string> read_file(const std::string& path);

/**
 * Write a string to a file, creating or overwriting it.
 * Returns true on success.
 */
bool write_file(const std::string& path, const std::string& contents);

/**
 * Return the size of a file in bytes, or -1 on error.
 */
ssize_t file_size(const std::string& path);

// ─── Process helpers ──────────────────────────────────────────────────────────

/**
 * Execute a command and wait for it to finish.
 * Returns the exit code, or -1 on fork/exec failure.
 */
int exec_wait(const std::vector<std::string>& args);

/**
 * Fork and exec a command, returning the child PID without waiting.
 * Returns -1 on failure.
 */
pid_t exec_async(const std::vector<std::string>& args);

/**
 * Send SIGTERM to a process, then SIGKILL after timeout_ms if it hasn't exited.
 * Returns true if the process exited cleanly (SIGTERM was enough).
 */
bool terminate_gracefully(pid_t pid, int timeout_ms = 5000);

// ─── CPU / ABI detection ─────────────────────────────────────────────────────

/**
 * Returns true if the host CPU supports AArch32 execution state.
 * Determined by reading /proc/cpuinfo for "aarch32" feature flag
 * and checking /proc/sys/abi/cp15_barrier_emulation as a secondary signal.
 *
 * Returns false on:
 * - Google Tensor G3 (Pixel 8 series)
 * - MediaTek Dimensity 8400-Ultra (POCO X7 Pro, etc.)
 * - Any other arm64-only SoC
 */
bool host_supports_aarch32();

/**
 * Returns the list of ABIs supported by the host as reported by
 * ro.product.cpu.abilist (via __system_property_get).
 */
std::vector<std::string> host_abi_list();

/**
 * Returns available RAM in megabytes from /proc/meminfo.
 */
long available_ram_mb();

} // namespace vine
