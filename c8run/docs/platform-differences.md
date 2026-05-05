# Platform Differences

C8Run supports Linux, macOS, and Windows. Implementation for each platform lives in `internal/unix/` (Linux + macOS) and `internal/windows/`. Changes to shared behavior must be reflected in both.

## Process Creation

| Behavior | Unix (`internal/unix/`) | Windows (`internal/windows/`) |
|---|---|---|
| Process group | `Setpgid: true` — new process group for signal handling | `CREATE_NEW_PROCESS_GROUP` flag |
| Console window | n/a | `CREATE_NO_WINDOW` flag suppresses console |
| SysProcAttr flags | `syscall.SysProcAttr{Setpgid: true}` | `syscall.SysProcAttr{CreationFlags: 0x08000000 \| 0x00000200}` |

On Unix, `Setpgid` ensures child processes belong to the same process group so signals propagate correctly. On Windows, both flags are required together — `CREATE_NEW_PROCESS_GROUP` alone does not suppress the console window.

## Process Kill

| Behavior | Unix | Windows |
|---|---|---|
| Kill implementation | `os.FindProcess()` + `Kill()` (SIGKILL) | `windows.TerminateProcess()` + `WaitForSingleObject()` |
| Child process enumeration | Not needed — Unix process groups handle tree | Explicit `processTree()` call to enumerate children |
| Already-exited process | Returns error from `Kill()` | `ERROR_INVALID_PARAMETER` — must be handled explicitly |

Unix returns a single PID for the process (group cleanup is OS-managed). Windows must explicitly enumerate and terminate child processes.

## Binary and Path Conventions

| Item | Unix | Windows |
|---|---|---|
| C8Run binary | `c8run` | `c8run.exe` |
| Camunda startup script | `./camunda-zeebe-{VERSION}/bin/camunda` | `.\camunda-zeebe-{VERSION}\bin\camunda.bat` |
| Path separator | `/` | `\` |
| Classpath separator | `:` | `;` |
| Connectors classpath | `{dir}/*:{dir}/custom_connectors/*` | `{dir}\*;{dir}\custom_connectors\*` |
| Java binary lookup | `java` | `java.exe` |
| Browser open command | `xdg-open` (Linux) / `open` (macOS) | `cmd /C start {url}` |

## Spring Config Paths

Spring Boot config paths use the native path separator. Unix uses `/`, Windows uses `\\` when constructing `--spring.config.additional-location` arguments. This is handled in `internal/start/startuphandler.go` — do not normalise separators across platforms.

## Archive Format

Distribution archives produced by the packager differ by platform:

| Platform | Format |
|---|---|
| Linux | `.tar.gz` |
| macOS | `.zip` |
| Windows | `.zip` |

Linux `.tar.gz` creation is used even on Windows if the archive type is explicitly requested (e.g. internal use). User-facing distribution archives always use the platform-native format above.

## Architecture Detection

| `runtime.GOARCH` | Mapped to |
|---|---|
| `amd64` | `x86_64` |
| `arm64` | `aarch64` |
| Windows (any) | `x86_64` (hardcoded) |
| Other | Error — unsupported architecture |

## Test Conventions

Platform-specific tests use build tags to restrict execution:

```go
//go:build !windows   // Unix-only test
//go:build windows    // Windows-only test
```

Unix tests check process liveness with `syscall.Kill(pid, 0)`. Windows tests use `windows.GetExitCodeProcess()`. When adding tests for platform-specific packages, always include the appropriate build tag and a matching test (or explicit comment) for the other platform.
