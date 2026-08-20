//go:build !windows

package processmanagement

import "syscall"

func (p *ProcessHandler) IsPidRunning(pid int) bool {
	if pid <= 0 {
		return false
	}
	err := syscall.Kill(pid, 0)
	return err == nil
}
