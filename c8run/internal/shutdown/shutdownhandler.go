package shutdown

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

func ShutdownProcesses(state *types.State) {
	log.Info().Msg("Initiating shutdown of services...")
	c8 := state.C8
	settings := state.Settings
	processInfo := state.ProcessInfo

	timeout := 30 * time.Second
	log.Info().Str("timeout", timeout.String()).Msg("Stopping all services...")

	progressChars := []string{"|", "/", "-", "\\"}
	tick := time.NewTicker(100 * time.Millisecond)
	done := make(chan struct{})
	go func() {
		i := 0
		for {
			select {
			case <-tick.C:
				fmt.Printf("\rStopping all services... %s", progressChars[i%len(progressChars)])
				i++
			case <-done:
				tick.Stop()
				return
			}
		}
	}()

	// fmt.Println("Shutting down processesgor...")
	log.Debug().Msg("stopCommand goroutine initiated")
	go func() {
		stopCommand(c8, settings, processInfo)
		close(done)
	}()

	select {
	case <-done:
		log.Info().Msg("All services have been stopped.")
	case <-time.After(timeout):
		fmt.Println()
		log.Warn().Msg("Warning: Timeout while stopping services. Some processes may still be running.")
	}
}

func stopCommand(c8 types.C8Run, settings types.C8RunSettings, processes types.Processes) {
	if !settings.DisableElasticsearch {
		log.Info().Msg("Trying to stop Elasticsearch")

		err := stopProcess(c8, processes.Elasticsearch.Pid)
		if err != nil {
			log.Debug().Err(err).Msg("Failed to stop Elasticsearch")
		} else {
			log.Info().Msg("Elasticsearch is stopped.")
		}
	}
	err := stopProcess(c8, processes.Connectors.Pid)
	if err != nil {
		log.Debug().Err(err).Msg("Failed to stop connectors")
	} else {
		log.Info().Msg("Connectors is stopped.")
	}
	err = stopProcess(c8, processes.Camunda.Pid)
	if err != nil {
		log.Debug().Err(err).Msg("Failed to stop Camunda")
	} else {
		log.Info().Msg("Camunda is stopped.")
	}
}

func readPIDFromFile(pidfile string) (int, error) {
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

func stopProcess(c8 types.C8Run, pidfile string) error {
	log.Debug().Str("pidFile", pidfile).Msg("Attempting to stop process via pidfile")

	if pidfile == "" {
		return errors.New("stopProcess: pidfile path is empty")
	}

	if _, err := os.Stat(pidfile); err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("stopProcess: pidfile does not exist: %s", pidfile)
		}
		return fmt.Errorf("stopProcess: unable to stat pidfile: %w", err)
	}

	pid, err := readPIDFromFile(pidfile)
	if err != nil {
		return fmt.Errorf("stopProcess: %w", err)
	}

	var killErr error
	for _, proc := range c8.ProcessTree(pid) {
		if proc == nil {
			continue
		}

		log.Debug().Int("pid", proc.Pid).Msg("Sending SIGKILL to process")
		if err := proc.Kill(); err != nil && !errors.Is(err, os.ErrProcessDone) {
			log.Warn().Err(err).Int("pid", proc.Pid).Msg("Failed to kill process")
			// record the first error but continue attempting to stop remaining processes
			if killErr == nil {
				killErr = err
			}
		}
		// Ensure we wait so the OS can release resources; ignore wait error.
		_, _ = proc.Wait()
	}

	if err := os.Remove(pidfile); err != nil && !errors.Is(err, os.ErrNotExist) {
		log.Warn().Err(err).Str("pidFile", pidfile).Msg("Failed to remove pidfile")
	}

	if killErr != nil {
		return fmt.Errorf("stopProcess: %w", killErr)
	}

	log.Info().Str("pidFile", pidfile).Msg("Successfully stopped process")

	return nil
}
