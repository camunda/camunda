package processmanagement

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/gofrs/flock"
	"github.com/rs/zerolog/log"
)

type ProcessHandler struct {
	C8 types.C8Run
}

func (p *ProcessHandler) cleanUp(pidPath string) {
	fileLock := flock.New(pidPath + ".lock")
	if err := fileLock.Lock(); err != nil {
		log.Err(err).Msgf("Failed to acquire lock for pid file: %s", pidPath)
		return
	}
	defer fileLock.Unlock()

	if err := os.Remove(pidPath); err != nil {
		log.Err(err).Msgf("Failed to remove pid file: %s", pidPath)
	}
}

func (p *ProcessHandler) ReadPIDFromFile(pidfile string) (int, error) {
	fileLock := flock.New(pidfile + ".lock")
	if err := fileLock.RLock(); err != nil {
		return 0, fmt.Errorf("failed to acquire read lock for pid file: %s: %w", pidfile, err)
	}
	defer fileLock.Unlock()

	data, err := os.ReadFile(pidfile)
	if err != nil {
		return 0, err
	}

	pidStr := strings.TrimSpace(string(data))
	pid, err := strconv.Atoi(pidStr)
	if err != nil {
		return 0, fmt.Errorf("invalid PID in %s: %w", pidfile, err)
	}

	if pid <= 0 {
		return 0, fmt.Errorf("invalid PID (%d) in %s", pid, pidfile)
	}

	return pid, nil
}

func (p *ProcessHandler) GetProcessFromPid(pid int) []*os.Process {
	return p.C8.ProcessTree(pid)
}

func (p *ProcessHandler) AttemptToStartProcess(pidPath string, processName string, startProcess func(), healthCheck func() error, stop context.CancelFunc) {
	pid, err := p.ReadPIDFromFile(pidPath)
	if err != nil {
		log.Debug().Msg("Failed to read PID from file. This is expected for the first run.")
		log.Info().Msgf("No pid for %s", processName)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
		return
	}

	procs := p.GetProcessFromPid(pid)
	if len(procs) == 0 {
		log.Info().Msgf("%s is not running, starting...", processName)
		p.cleanUp(pidPath)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
		return
	}

	anyRunning := false
	for _, proc := range procs {
		if proc == nil {
			log.Warn().Msgf("Encountered nil process in process list for %s; skipping entry.", processName)
			continue
		}
		if p.IsPidRunning(proc.Pid) {
			anyRunning = true
			log.Debug().Int("pid", proc.Pid).Msgf("%s is running", processName)
			if err := healthCheck(); err == nil {
				log.Info().Msgf("%s is healthy, skipping...", processName)
				return
			} else {
				log.Info().Msgf("%s is not healthy, killing and restarting...", processName)
				if killErr := p.KillProcess(proc); killErr != nil {
					log.Err(killErr).Msgf("Failed to kill %s", processName)
					stop()
					return
				}
				p.cleanUp(pidPath)
				p.startAndCheck(processName, startProcess, healthCheck, stop)
				return
			}
		}
	}

	// If we reach here, no running and healthy process was found
	if !anyRunning {
		log.Info().Msgf("No running process found for %s, cleaning up and starting...", processName)
		p.cleanUp(pidPath)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
	}
}

// Helper to start and health check, stopping if unhealthy
func (p *ProcessHandler) startAndCheck(processName string, startProcess func(), healthCheck func() error, stop context.CancelFunc) {
	startProcess()
	if err := healthCheck(); err != nil {
		log.Err(err).Msgf("%s is not healthy after start", processName)
		stop()
	}
}

func (p *ProcessHandler) WritePIDToFile(pidPath string, pid int) error {
	fileLock := flock.New(pidPath + ".lock")
	if err := fileLock.Lock(); err != nil {
		log.Err(err).Msgf("Failed to acquire lock for pid file: %s", pidPath)
		return err
	}
	defer fileLock.Unlock()

	log.Info().Int("pid", pid).Msg("Started process: " + pidPath)
	pidFile, err := os.OpenFile(pidPath, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		log.Err(err).Msg("Failed to open Pid file: " + pidPath)
		return err
	}
	defer pidFile.Close()

	_, err = pidFile.Write([]byte(strconv.Itoa(pid)))
	if err != nil {
		log.Err(err).Msg("Failed to write to Pid file: " + pidPath)
		return err
	}
	return nil
}

func (p *ProcessHandler) KillProcess(proc *os.Process) error {
	var killErr error
	log.Debug().Int("pid", proc.Pid).Msg("Sending SIGKILL to process")
	if err := proc.Kill(); err != nil && !errors.Is(err, os.ErrProcessDone) {
		log.Warn().Err(err).Int("pid", proc.Pid).Msg("Failed to kill process")
		killErr = err
	}
	_, _ = proc.Wait()

	return killErr
}
