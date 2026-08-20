//go:build windows

package processmanagement

import (
	"errors"
	"fmt"

	"github.com/rs/zerolog/log"
	"golang.org/x/sys/windows"
)

func (p *ProcessHandler) KillProcess(pid int) error {
	if pid <= 0 {
		return nil
	}

	handle, err := windows.OpenProcess(windows.PROCESS_TERMINATE|windows.SYNCHRONIZE|windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(pid))
	if err != nil {
		if errors.Is(err, windows.ERROR_INVALID_PARAMETER) {
			// Process already exited.
			return nil
		}
		return fmt.Errorf("openProcess: %w", err)
	}
	defer windows.CloseHandle(handle)

	log.Debug().Int("pid", pid).Msg("Sending SIGKILL to process")
	if err := windows.TerminateProcess(handle, 1); err != nil {
		log.Warn().Err(err).Int("pid", pid).Msg("Failed to kill process")
		return fmt.Errorf("TerminateProcess: %w", err)
	}

	if _, waitErr := windows.WaitForSingleObject(handle, windows.INFINITE); waitErr != nil && !errors.Is(waitErr, windows.ERROR_INVALID_HANDLE) {
		log.Debug().Err(waitErr).Int("pid", pid).Msg("WaitForSingleObject failed after kill")
	}

	return nil
}
