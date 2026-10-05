//go:build !windows

package processmanagement

import (
	"errors"
	"os"
	"time"

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

	// Wait only reaps our own children; c8run stop kills processes started by an
	// earlier invocation, so poll until the PID is gone. Otherwise a start right
	// after stop can still find the old process holding its ports.
	if killErr == nil {
		deadline := time.Now().Add(killWaitTimeout)
		for p.IsPidRunning(pid) && time.Now().Before(deadline) {
			time.Sleep(killPollInterval)
		}
		if p.IsPidRunning(pid) {
			log.Warn().Int("pid", pid).Dur("timeout", killWaitTimeout).Msg("Process still running after SIGKILL")
		}
	}

	return killErr
}

const (
	killWaitTimeout  = 10 * time.Second
	killPollInterval = 50 * time.Millisecond
)
