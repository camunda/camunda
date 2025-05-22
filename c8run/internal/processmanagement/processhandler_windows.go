//go:build windows
// +build windows

package processmanagement

import (
	"errors"

	"golang.org/x/sys/windows"
)

func (p *ProcessHandler) IsPidRunning(pid int) bool {
	h, err := windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(pid))
	if err != nil {
		if errors.Is(err, windows.ERROR_INVALID_PARAMETER) {
			return false // PID doesn't exist
		}
		// If we got access denied, the process may still be running but
		// we don't have sufficient rights to query it (e.g. on locked-down
		// CI runners). Treat that as the process existing.
		if errors.Is(err, windows.ERROR_ACCESS_DENIED) {
			return true
		}
		return false
	}
	defer windows.CloseHandle(h)

	var code uint32
	if err = windows.GetExitCodeProcess(h, &code); err != nil {
		return false
	}
	return code == 259
}
