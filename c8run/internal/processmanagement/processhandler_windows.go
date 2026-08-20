//go:build windows

package processmanagement

import (
	"errors"

	"github.com/rs/zerolog/log"
	"golang.org/x/sys/windows"
)

func (p *ProcessHandler) IsPidRunning(pid int) bool {
	h, err := windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(pid))
	if err != nil {
		if errors.Is(err, windows.ERROR_INVALID_PARAMETER) {
			log.Debug().Msg("PID doesn't exist")
			return false // PID doesn't exist
		}
		log.Debug().Msg("Process not running")
		return false
	}
	defer windows.CloseHandle(h)

	var code uint32
	if err = windows.GetExitCodeProcess(h, &code); err != nil {
		return false
	}
	return code == 259
}
