package processmanagement

import (
	"context"
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

// acquireLock tries to acquire a lock and returns the lock or an error.
func acquireLock(lockPath string) (*flock.Flock, error) {
	fileLock := flock.New(lockPath)
	if err := fileLock.Lock(); err != nil {
		log.Err(err).Msgf("Failed to acquire lock for file: %s", lockPath)
		return nil, err
	}
	return fileLock, nil
}

// acquireRLock tries to acquire a read lock and returns the lock or an error.
func acquireRLock(lockPath string) (*flock.Flock, error) {
	fileLock := flock.New(lockPath)
	if err := fileLock.RLock(); err != nil {
		log.Err(err).Msgf("Failed to acquire read lock for file: %s", lockPath)
		return nil, err
	}
	return fileLock, nil
}

// releaseLock unlocks the lock and logs any error.
func releaseLock(fileLock *flock.Flock, lockPath string) {
	if err := fileLock.Unlock(); err != nil {
		log.Err(err).Msgf("Failed to unlock file: %s", lockPath)
	}
}

func (p *ProcessHandler) cleanUp(pidPath string) {
	lockPath := pidPath + ".lock"
	fileLock, err := acquireLock(lockPath)
	if err != nil {
		return
	}
	defer func() {
		releaseLock(fileLock, lockPath)
		if err := os.Remove(lockPath); err != nil && !os.IsNotExist(err) {
			log.Err(err).Msgf("Failed to remove lock file: %s", lockPath)
		}
	}()

	if err := os.Remove(pidPath); err != nil && !os.IsNotExist(err) {
		log.Err(err).Msgf("Failed to remove pid file: %s", pidPath)
	}
}

func (p *ProcessHandler) ReadPIDFromFile(pidfile string) (int, error) {
	lockPath := pidfile + ".lock"
	fileLock, err := acquireRLock(lockPath)
	if err != nil {
		return 0, fmt.Errorf("failed to acquire read lock for pid file: %s: %w", pidfile, err)
	}
	defer releaseLock(fileLock, lockPath)

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

func (p *ProcessHandler) GetProcessFromPid(pid int) []int {
	return p.C8.ProcessTree(pid)
}

// AttemptToStartProcess checks if a process is running and healthy, and starts or restarts it as needed.
// It uses the provided startProcess and healthCheck functions, and calls stop if the process is unhealthy or cannot be started.
//
// | Process Running | Process Healthy | Action Taken                                   |
// |-----------------|----------------|------------------------------------------------|
// | No              | N/A            | Start process                                  |
// | Yes             | Yes            | Do nothing (process is healthy and running)    |
// | Yes             | No             | Kill and restart process                       |
// | No (stale PID)  | N/A            | Clean up, start process                        |
//
// If the process is not running or not healthy, it will be killed (if needed), cleaned up, and restarted.
// If the process is running and healthy, nothing is done.
func (p *ProcessHandler) AttemptToStartProcess(pidPath string, processName string, startProcess func(), healthCheck func() error, stop context.CancelFunc) {
	pid, err := p.ReadPIDFromFile(pidPath)
	if err != nil {
		log.Debug().Msg("Failed to read PID from file. This is expected for the first run.")
		log.Info().Msgf("No pid for %s", processName)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
		return
	}

	processPids := p.GetProcessFromPid(pid)
	if len(processPids) == 0 {
		log.Info().Msgf("%s is not running, starting...", processName)
		p.cleanUp(pidPath)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
		return
	}

	for _, procPid := range processPids {
		if procPid <= 0 {
			log.Warn().Msgf("Encountered invalid PID in process list for %s; skipping entry.", processName)
			continue
		}
		if p.IsPidRunning(procPid) {
			log.Debug().Int("pid", procPid).Msgf("%s is running", processName)
			if err := healthCheck(); err == nil {
				log.Info().Msgf("%s is healthy, skipping...", processName)
				return
			} else {
				log.Info().Msgf("%s is not healthy, killing and restarting...", processName)
				if killErr := p.KillProcess(procPid); killErr != nil {
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

	log.Info().Msgf("No running process found for %s, cleaning up and starting...", processName)
	p.cleanUp(pidPath)
	p.startAndCheck(processName, startProcess, healthCheck, stop)
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
	lockPath := pidPath + ".lock"
	fileLock, err := acquireLock(lockPath)
	if err != nil {
		return err
	}
	defer releaseLock(fileLock, lockPath)

	log.Info().Int("pid", pid).Msg("Started process: " + pidPath)
	pidFile, err := os.OpenFile(pidPath, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		log.Err(err).Msg("Failed to open Pid file: " + pidPath)
		return err
	}
	defer func() {
		if err := pidFile.Close(); err != nil {
			log.Error().Err(err).Msg("failed to close pid file")
		}
	}()

	_, err = pidFile.Write([]byte(strconv.Itoa(pid)))
	if err != nil {
		log.Err(err).Msg("Failed to write to Pid file: " + pidPath)
		return err
	}
	return nil
}
