#pragma once

#include <string>
#include <vector>
#include <optional>
#include <sys/types.h>

namespace vine {

bool mkdirs(const std::string& path, mode_t mode = 0755);
bool path_exists(const std::string& path);
bool is_directory(const std::string& path);

// Returns nullopt on failure.
std::optional<std::string> read_file(const std::string& path);

// Creates or overwrites the file. Returns true on success.
bool write_file(const std::string& path, const std::string& contents);

// Returns -1 on error.
ssize_t file_size(const std::string& path);

// Execute and wait. Returns exit code or -1 on fork/exec failure.
int exec_wait(const std::vector<std::string>& args);

// Fork and exec without waiting. Returns child PID or -1 on failure.
pid_t exec_async(const std::vector<std::string>& args);

// Sends SIGTERM, then SIGKILL after timeout_ms if still alive; returns true if the process exited on SIGTERM
bool terminate_gracefully(pid_t pid, int timeout_ms = 5000);

// True if the host CPU supports AArch32 execution state; checks abilist32, abilist, /proc/sys/abi/*, and cpuinfo, false on the arm64-only SoCs common across many vendors
bool host_supports_aarch32();

// Returns ABIs from ro.product.cpu.abilist.
std::vector<std::string> host_abi_list();

// Returns available RAM in megabytes.
long available_ram_mb();

} // namespace vine
