package shutdown

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/camunda/camunda/c8run/internal/processmanagement"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
	"gopkg.in/yaml.v3"
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

	if shouldDeleteDataDir(settings, processes) {
		deleteDataDir(processes)
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

func shouldDeleteDataDir(settings types.C8RunSettings, processes types.Processes) bool {
	// Only consider deletion when using H2 (default) secondary storage.
	if settings.SecondaryStorageType == "" || strings.EqualFold(settings.SecondaryStorageType, "elasticsearch") {
		return false
	}

	// Highest precedence: explicit env override.
	if url, ok := os.LookupEnv("CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL"); ok {
		return strings.HasPrefix(strings.ToLower(strings.TrimSpace(url)), "jdbc:h2:")
	}

	baseDir := filepath.Dir(processes.Camunda.PidPath)
	for _, cfg := range resolveConfigPaths(baseDir, settings.Config) {
		if url, err := detectRdbmsURL(cfg); err == nil && strings.HasPrefix(strings.ToLower(url), "jdbc:h2:") {
			return true
		}
	}

	return false
}

func deleteDataDir(processes types.Processes) {
	baseDir := filepath.Dir(processes.Camunda.PidPath)
	dataDir := filepath.Join(baseDir, "camunda-zeebe-"+processes.Camunda.Version, "data")
	if dataDir == "" {
		return
	}

	if err := os.RemoveAll(dataDir); err != nil {
		log.Warn().Err(err).Str("dataDir", dataDir).Msg("Failed to delete data directory for H2 cleanup")
		return
	}

	log.Info().Str("dataDir", dataDir).Msg("Deleted data directory to keep H2 in-memory state consistent across restarts")
}

func resolveConfigPaths(baseDir string, userConfig string) []string {
	var paths []string
	if userConfig != "" {
		candidate := filepath.Join(baseDir, userConfig)
		if info, err := os.Stat(candidate); err == nil {
			if info.IsDir() {
				candidate = filepath.Join(candidate, "application.yaml")
			}
			paths = append(paths, candidate)
		}
	}
	defaultConfig := filepath.Join(baseDir, "configuration", "application.yaml")
	paths = append(paths, defaultConfig)
	return paths
}

func detectRdbmsURL(path string) (string, error) {
	info, err := os.Stat(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return "", nil
		}
		return "", err
	}
	if info.IsDir() {
		return detectRdbmsURL(filepath.Join(path, "application.yaml"))
	}

	content, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return "", nil
		}
		return "", err
	}

	if len(strings.TrimSpace(string(content))) == 0 {
		return "", nil
	}

	var root map[string]any
	if err := yaml.Unmarshal(content, &root); err != nil {
		return "", err
	}

	camundaNode, ok := root["camunda"].(map[string]any)
	if !ok {
		return "", nil
	}
	dataNode, ok := camundaNode["data"].(map[string]any)
	if !ok {
		return "", nil
	}
	secondaryNode, ok := dataNode["secondary-storage"].(map[string]any)
	if !ok {
		return "", nil
	}
	rdbmsNode, ok := secondaryNode["rdbms"].(map[string]any)
	if !ok {
		return "", nil
	}
	if url, ok := rdbmsNode["url"].(string); ok {
		return strings.TrimSpace(url), nil
	}
	return "", nil
}
