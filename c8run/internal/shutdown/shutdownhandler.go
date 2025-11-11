package shutdown

import (
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/camunda/camunda/c8run/internal/processmanagement"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

type ShutdownHandler struct {
	ProcessHandler *processmanagement.ProcessHandler
}

func (s *ShutdownHandler) ShutdownProcesses(state *types.State) {
	log.Info().Msg("Initiating shutdown of services...")
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

	log.Debug().Msg("stopCommand goroutine initiated")
	go func() {
		s.stopCommand(settings, processInfo)
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

func (s *ShutdownHandler) stopCommand(settings types.C8RunSettings, processes types.Processes) {
	if !settings.DisableElasticsearch {
		log.Info().Msg("Trying to stop Elasticsearch")

		err := s.stopProcess(processes.Elasticsearch.PidPath)
		if err != nil {
			log.Debug().Err(err).Msg("Failed to stop Elasticsearch")
		} else {
			log.Info().Msg("Elasticsearch is stopped.")
		}
	}
	err := s.stopProcess(processes.Connectors.PidPath)
	if err != nil {
		log.Debug().Err(err).Msg("Failed to stop connectors")
	} else {
		log.Info().Msg("Connectors is stopped.")
	}
	err = s.stopProcess(processes.Camunda.PidPath)
	if err != nil {
		log.Debug().Err(err).Msg("Failed to stop Camunda")
	} else {
		log.Info().Msg("Camunda is stopped.")
	}
}

func (s *ShutdownHandler) stopProcess(pidPath string) error {
	log.Debug().Str("pidFile", pidPath).Msg("Attempting to stop process via pidfile")

	if pidPath == "" {
		return errors.New("stopProcess: pidfile path is empty")
	}

	if _, err := os.Stat(pidPath); err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("stopProcess: pidfile does not exist: %s", pidPath)
		}
		return fmt.Errorf("stopProcess: unable to stat pidfile: %w", err)
	}

	pid, err := s.ProcessHandler.ReadPIDFromFile(pidPath)
	if err != nil {
		return fmt.Errorf("stopProcess: %w", err)
	}

	var killErr error
	processPids := s.ProcessHandler.GetProcessFromPid(pid)
	for _, procPid := range processPids {
		if procPid <= 0 {
			continue
		}

		killErr = s.ProcessHandler.KillProcess(procPid)
	}

	if err := os.Remove(pidPath); err != nil && !errors.Is(err, os.ErrNotExist) {
		log.Warn().Err(err).Str("pidFile", pidPath).Msg("Failed to remove pidfile")
	}

	if killErr != nil {
		return fmt.Errorf("stopProcess: %w", killErr)
	}

	log.Info().Str("pidFile", pidPath).Msg("Successfully stopped process")

	return nil
}
