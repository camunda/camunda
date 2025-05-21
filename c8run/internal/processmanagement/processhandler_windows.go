//go:build windows
// +build windows

package processmanagement

import (
	"os"
	"syscall"
)

func (p *ProcessHandler) IsPidRunning(pid int) bool {
	process, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return process.Signal(syscall.Signal(0)) == nil
}
