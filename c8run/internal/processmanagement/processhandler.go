package processmanagement

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

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
	pids, err := p.ReadPIDsFromFile(pidfile)
	if err != nil {
		return 0, err
	}
	if len(pids) == 0 {
		return 0, errors.New("pidfile does not contain any pid entries")
	}
	return pids[0], nil
}

func (p *ProcessHandler) ReadPIDsFromFile(pidfile string) ([]int, error) {
	lockPath := pidfile + ".lock"
	fileLock, err := acquireRLock(lockPath)
	if err != nil {
		return nil, fmt.Errorf("failed to acquire read lock for pid file: %s: %w", pidfile, err)
	}
	defer releaseLock(fileLock, lockPath)

	data, err := os.ReadFile(pidfile)
	if err != nil {
		return nil, err
	}

	rawEntries := strings.FieldsFunc(string(data), func(r rune) bool {
		return r == '\n' || r == '\r' || r == ',' || r == ';'
	})

	pids := make([]int, 0, len(rawEntries))
	seen := make(map[int]struct{})
	for _, entry := range rawEntries {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		pid, err := strconv.Atoi(entry)
		if err != nil {
			return nil, fmt.Errorf("invalid PID in %s: %w", pidfile, err)
		}
		if pid <= 0 {
			return nil, fmt.Errorf("invalid PID (%d) in %s", pid, pidfile)
		}
		if _, ok := seen[pid]; ok {
			continue
		}
		seen[pid] = struct{}{}
		pids = append(pids, pid)
	}

	return pids, nil
}

func (p *ProcessHandler) WritePIDsToFile(pidPath string, pids []int) error {
	lockPath := pidPath + ".lock"
	fileLock, err := acquireLock(lockPath)
	if err != nil {
		return err
	}
	defer releaseLock(fileLock, lockPath)

	validInts := dedupeIntSlice(pids)
	validPIDs := make([]string, 0, len(validInts))
	for _, pid := range validInts {
		validPIDs = append(validPIDs, strconv.Itoa(pid))
	}

	if len(validPIDs) == 0 {
		return errors.New("no valid PIDs supplied")
	}
	pidFile, err := os.OpenFile(pidPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0644)
	if err != nil {
		log.Err(err).Msg("Failed to open Pid file: " + pidPath)
		return err
	}
	defer func() {
		if err := pidFile.Close(); err != nil {
			log.Error().Err(err).Msg("failed to close pid file")
		}
	}()

	_, err = pidFile.Write([]byte(strings.Join(validPIDs, "\n")))
	if err != nil {
		log.Err(err).Msg("Failed to write to Pid file: " + pidPath)
		return err
	}
	return nil
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
	pidList, err := p.ReadPIDsFromFile(pidPath)
	if err != nil {
		log.Debug().Msg("Failed to read PID from file. This is expected for the first run.")
		log.Info().Msgf("No pid for %s", processName)
		p.startAndCheck(processName, startProcess, healthCheck, stop)
		return
	}

	processPids := p.CollectCandidatePIDs(pidList)
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
	log.Info().Int("pid", pid).Msg("Started process: " + pidPath)
	return p.WritePIDsToFile(pidPath, []int{pid})
}

func dedupeIntSlice(values []int) []int {
	seen := make(map[int]struct{}, len(values))
	result := make([]int, 0, len(values))
	for _, v := range values {
		if v <= 0 {
			continue
		}
		if _, ok := seen[v]; ok {
			continue
		}
		seen[v] = struct{}{}
		result = append(result, v)
	}
	return result
}

// TrackProcessTree continually attempts to capture the descendant process IDs and persists them.
// This is mainly required for Windows where startup scripts spawn child processes
// that may survive even if the wrapper process exits.
func (p *ProcessHandler) TrackProcessTree(pidPath string, rootPid int) {
	if rootPid <= 0 {
		return
	}

	go func() {
		ticker := time.NewTicker(500 * time.Millisecond)
		defer ticker.Stop()
		timeout := time.After(15 * time.Second)
		var lastWritten []int

		writeIfChanged := func(tree []int) bool {
			clean := dedupeIntSlice(tree)
			if len(clean) <= 1 {
				return false
			}
			if slicesEqual(clean, lastWritten) {
				return false
			}
			if err := p.WritePIDsToFile(pidPath, clean); err != nil {
				log.Debug().Err(err).Str("pidFile", pidPath).Msg("Failed to update pidfile with process tree")
				return false
			}
			lastWritten = append([]int(nil), clean...)
			log.Debug().Ints("pids", clean).Str("pidFile", pidPath).Msg("Recorded process tree")
			return true
		}

		for {
			tree := p.GetProcessFromPid(rootPid)
			if writeIfChanged(tree) {
				return
			}

			select {
			case <-ticker.C:
				continue
			case <-timeout:
				return
			}
		}
	}()
}

func slicesEqual(a, b []int) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func (p *ProcessHandler) CollectCandidatePIDs(pidList []int) []int {
	candidates := make(map[int]struct{})
	for _, pid := range pidList {
		if pid <= 0 {
			continue
		}
		candidates[pid] = struct{}{}
		tree := p.GetProcessFromPid(pid)
		for _, descendant := range tree {
			if descendant <= 0 {
				continue
			}
			candidates[descendant] = struct{}{}
		}
	}

	result := make([]int, 0, len(candidates))
	for pid := range candidates {
		result = append(result, pid)
	}
	return result
}
