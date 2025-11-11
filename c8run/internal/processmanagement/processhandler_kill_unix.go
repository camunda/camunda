//go:build !windows

package processmanagement

import (
	"errors"
	"os"

	"github.com/rs/zerolog/log"
)

func (p *ProcessHandler) KillProcess(pid int) error {
	if pid <= 0 {
		return nil
	}

	proc, err := os.FindProcess(pid)
	if err != nil {
		return err
	}

	var killErr error
	log.Debug().Int("pid", pid).Msg("Sending SIGKILL to process")
	if err := proc.Kill(); err != nil && !errors.Is(err, os.ErrProcessDone) {
		log.Warn().Err(err).Int("pid", pid).Msg("Failed to kill process")
		killErr = err
	}
	_, _ = proc.Wait()

	return killErr
}
