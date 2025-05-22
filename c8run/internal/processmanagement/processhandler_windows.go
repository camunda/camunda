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
		return false
	}
	defer windows.CloseHandle(h)

	var code uint32
	if err = windows.GetExitCodeProcess(h, &code); err != nil {
		return false
	}
	return code == uint32(259)
}
