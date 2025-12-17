package start

import (
	"context"
	"errors"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"

	"github.com/camunda/camunda/c8run/internal/health"
	"github.com/camunda/camunda/c8run/internal/overrides"
	"github.com/camunda/camunda/c8run/internal/processmanagement"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

var evalSymlinks = filepath.EvalSymlinks

func printSystemInformation(javaVersion, javaHome, javaOpts string, usingElasticsearch bool) {
	fmt.Println("")
	fmt.Println("")
	fmt.Println("System Version Information")
	fmt.Println("--------------------------")
	fmt.Println("Camunda Details:")
	fmt.Printf("  Version: %s\n", os.Getenv("CAMUNDA_VERSION"))
	fmt.Println("--------------------------")
	fmt.Println("Java Details:")
	fmt.Printf("  Version: %s\n", javaVersion)
	fmt.Printf("  JAVA_HOME: %s\n", javaHome)
	fmt.Printf("  JAVA_OPTS: %s\n", javaOpts)
	fmt.Println("--------------------------")
	fmt.Println("Logging Details:")
	if usingElasticsearch {
		fmt.Println("  Elasticsearch: ./log/elasticsearch.log")
	}
	fmt.Println("  Connectors: ./log/connectors.log")
	fmt.Println("  Camunda: ./log/camunda.log")
	fmt.Println("--------------------------")
	fmt.Println("Press Ctrl+C to initiate graceful shutdown.")
	fmt.Println("--------------------------")
	fmt.Println("")
	fmt.Println("")
}

func getJavaVersion(javaBinary string) (string, error) {
	javaVersionCmd := exec.Command(javaBinary, "JavaVersion")
	var out strings.Builder
	var stderr strings.Builder
	javaVersionCmd.Stdout = &out
	javaVersionCmd.Stderr = &stderr
	err := javaVersionCmd.Run()
	if err != nil {
		return "", fmt.Errorf("failed to run java version command: %w", err)
	}
	javaVersionOutput := out.String()
	return javaVersionOutput, nil
}

type StartupHandler struct {
	ProcessHandler *processmanagement.ProcessHandler
}

func ensurePortAvailable(port int) error {
	networks := []string{"tcp4", "tcp6"}
	for _, network := range networks {
		listener, err := net.Listen(network, fmt.Sprintf(":%d", port))
		if err != nil {
			if isNetworkUnsupported(err) {
				continue
			}
			return fmt.Errorf("port %d is already in use (%s)", port, network)
		}
		if err := listener.Close(); err != nil {
			return fmt.Errorf("failed to release temporary port check listener: %w", err)
		}
	}
	return nil
}

func isNetworkUnsupported(err error) bool {
	switch {
	case errors.Is(err, syscall.EAFNOSUPPORT),
		errors.Is(err, syscall.EPROTONOSUPPORT),
		errors.Is(err, syscall.EPFNOSUPPORT),
		errors.Is(err, syscall.EADDRNOTAVAIL):
		return true
	default:
		return false
	}
}

func (s *StartupHandler) startApplication(cmd *exec.Cmd, pid string, logPath string, stop context.CancelFunc) error {
	logFile, err := os.OpenFile(logPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0644)
	if err != nil {
		log.Err(err).Msg("Failed to open file: " + logPath)
		return err
	}

	defer func() {
		if cerr := logFile.Close(); cerr != nil {
			log.Err(cerr).Msg("Failed to close file: " + logPath)
		}
	}()

	cmd.Stdout = logFile
	cmd.Stderr = logFile
	err = cmd.Start()
	if err != nil {
		log.Err(err).Msg("Failed to start process: " + cmd.String())
		return err
	}

	err = s.ProcessHandler.WritePIDToFile(pid, cmd.Process.Pid)
	if err != nil {
		log.Err(err).Msg("Failed to write PID to file: " + pid)
		log.Info().Msg("To avoid zombie processes, we will now kill all processes that have the same PID as the process we just started and quit the application")
		stop()
		return err
	}

	return nil
}

func getJavaHome(javaBinary string) (string, error) {
	javaHomeCmd := exec.Command(javaBinary, "JavaHome")
	var out strings.Builder
	var stderr strings.Builder
	javaHomeCmd.Stdout = &out
	javaHomeCmd.Stderr = &stderr
	err := javaHomeCmd.Run()
	if err != nil {
		return "", fmt.Errorf("failed to run java version command: %w", err)
	}
	javaHomeOutput := out.String()
	return javaHomeOutput, nil
}

func resolveJavaHomeAndBinary() (string, string, error) {
	javaHome := os.Getenv("JAVA_HOME")
	javaBinary := "java"
	var javaHomeAfterSymlink string
	var err error
	if javaHome != "" {
		javaHomeAfterSymlink, err = evalSymlinks(javaHome)
		if err != nil {
			if _, statErr := os.Stat(javaHome); statErr != nil {
				log.Debug().Err(err).Msg("JAVA_HOME is not a valid path, obtaining JAVA_HOME from java binary")
				javaHome = ""
			} else {
				log.Debug().Err(err).Msg("Failed to resolve JAVA_HOME symlinks, continuing with provided path")
			}
		} else {
			javaHome = javaHomeAfterSymlink
		}
	}
	if javaHome == "" {
		javaHome, err = getJavaHome(javaBinary)
		if err != nil {
			return "", "", fmt.Errorf("failed to get JAVA_HOME")
		}
	}

	if javaHome != "" {
		err = filepath.Walk(javaHome, func(path string, info os.FileInfo, err error) error {
			_, filename := filepath.Split(path)
			if strings.Compare(filename, "java.exe") == 0 || strings.Compare(filename, "java") == 0 {
				javaBinary = path
				return filepath.SkipAll
			}
			return nil
		})
		if err != nil {
			return "", "", err
		}
		// fallback to bin/java.exe
		if javaBinary == "" {
			switch runtime.GOOS {
			case "linux", "darwin":
				javaBinary = filepath.Join(javaHome, "bin", "java")
			case "windows":
				javaBinary = filepath.Join(javaHome, "bin", "java.exe")
			}
		}
	} else {
		path, err := exec.LookPath("java")
		if err != nil {
			return "", "", fmt.Errorf("failed to find JAVA_HOME or java program")
		}

		// go up 2 directories since it's not guaranteed that java is in a bin folder
		javaHome = filepath.Dir(filepath.Dir(path))
		javaBinary = path
	}

	return javaHome, javaBinary, nil
}

// ensureDefaultConfig verifies that <parentDir>/configuration/default.yaml exists.
func ensureDefaultConfig(parentDir string) error {
	configDir := filepath.Join(parentDir, "configuration")
	appYAML := filepath.Join(configDir, "application.yaml")

	if err := os.MkdirAll(configDir, 0o755); err != nil {
		return fmt.Errorf("failed to create configuration directory: %w", err)
	}
	if _, err := os.Stat(appYAML); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("missing default config at %s (expected /configuration/application.yaml). Please add it to your repo", appYAML)
		}
		return fmt.Errorf("failed to stat %s: %w", appYAML, err)
	}
	return nil
}

func (s *StartupHandler) StartCommand(wg *sync.WaitGroup, ctx context.Context, stop context.CancelFunc, state *types.State, parentDir string) {
	defer wg.Done()

	c8 := state.C8
	settings := state.Settings
	processInfo := state.ProcessInfo

	// Check if Elasticsearch should be started (only if secondary-storage.type is elasticsearch)
	shouldStartElasticsearch := !settings.DisableElasticsearch

	// Resolve JAVA_HOME and javaBinary
	javaHome, javaBinary, err := resolveJavaHomeAndBinary()
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	if err := ensureDefaultConfig(parentDir); err != nil {
		fmt.Printf("Failed to ensure default config: %v\n", err)
		os.Exit(1)
	}

	if err := ensurePortAvailable(settings.Port); err != nil {
		log.Error().Err(err).Int("port", settings.Port).Msg("Camunda Run port is unavailable")
		fmt.Printf("Port %d is already in use. Stop the other service or run `c8run start --port <free-port>`.\n", settings.Port)
		os.Exit(1)
	}

	err = overrides.SetEnvVars(javaHome, shouldStartElasticsearch)
	if err != nil {
		fmt.Println("Failed to set envVars:", err)
	}
	javaVersion := os.Getenv("JAVA_VERSION")
	if javaVersion == "" {
		javaVersion, err = getJavaVersion(javaBinary)
		if err != nil {
			fmt.Println("Failed to get Java version")
			os.Exit(1)
		}
	}

	expectedJavaVersion := 21

	versionSplit := strings.Split(javaVersion, ".")
	if len(versionSplit) == 0 {
		fmt.Println("Java needs to be installed. Please install JDK " + strconv.Itoa(expectedJavaVersion) + " or newer.")
		os.Exit(1)
	}
	javaMajorVersion := versionSplit[0]
	javaMajorVersionInt, _ := strconv.Atoi(javaMajorVersion)
	if javaMajorVersionInt < expectedJavaVersion {
		fmt.Print("You must use at least JDK " + strconv.Itoa(expectedJavaVersion) + " to start Camunda Platform Run.\n")
		os.Exit(1)
	}

	javaOpts := os.Getenv("JAVA_OPTS")
	if javaOpts != "" {
		fmt.Print("JAVA_OPTS: " + javaOpts + "\n")
	}
	javaOpts = overrides.AdjustJavaOpts(javaOpts, settings)

	if shouldStartElasticsearch && settings.SecondaryStorageType != "" && !strings.EqualFold(settings.SecondaryStorageType, "elasticsearch") {
		shouldStartElasticsearch = false
		log.Info().
			Str("secondaryStorage.type", settings.SecondaryStorageType).
			Msg("Skipping Elasticsearch startup because configuration selects a different secondary storage backend")
	}

	printSystemInformation(javaVersion, javaHome, javaOpts, shouldStartElasticsearch)
	if shouldStartElasticsearch {
		elasticHealthEndpoint := "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s"
		s.ProcessHandler.AttemptToStartProcess(processInfo.Elasticsearch.PidPath, "Elasticsearch", func() {
			elasticsearchCmd := c8.ElasticsearchCmd(ctx, processInfo.Elasticsearch.Version, parentDir)
			elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
			err := s.startApplication(elasticsearchCmd, processInfo.Elasticsearch.PidPath, elasticsearchLogFilePath, stop)
			if err != nil {
				log.Err(err).Msg("Failed to start Elasticsearch")
				stop()
				return
			}
		}, func() error {
			return health.QueryElasticsearch(ctx, "Elasticsearch", 12, elasticHealthEndpoint)
		}, stop)
	}

	var extraArgs string
	var slash string
	switch runtime.GOOS {
	case "linux", "darwin":
		slash = "/"
	case "windows":
		slash = "\\"
	}

	// Always load the default config directory
	extraArgs = "--spring.config.additional-location=file:" + filepath.Join(parentDir, "configuration") + slash

	// Optional user override (file or dir) — appended LAST => higher precedence
	if settings.Config != "" {
		path := filepath.Join(parentDir, settings.Config)
		configStat, err := os.Stat(path)
		if err != nil {
			fmt.Printf("Failed to read config path: %s\n", path)
			os.Exit(1)
		}
		if configStat.IsDir() {
			extraArgs = extraArgs + ",file:" + path + slash
		} else {
			extraArgs = extraArgs + ",file:" + path
		}
	}

	s.ProcessHandler.AttemptToStartProcess(processInfo.Connectors.PidPath, "Connectors", func() {
		connectorsCmd := c8.ConnectorsCmd(ctx, javaBinary, parentDir, processInfo.Camunda.Version, state.Settings.Port)
		connectorsLogPath := filepath.Join(parentDir, "log", "connectors.log")
		err := s.startApplication(connectorsCmd, processInfo.Connectors.PidPath, connectorsLogPath, stop)
		if err != nil {
			log.Err(err).Msg("Failed to start Connectors process")
			stop()
			return
		}
	}, func() error {
		// TODO do a health check on the connectors process
		return nil
	}, stop)

	s.ProcessHandler.AttemptToStartProcess(processInfo.Camunda.PidPath, "Camunda", func() {
		camundaCmd := c8.CamundaCmd(ctx, processInfo.Camunda.Version, parentDir, extraArgs, javaOpts)
		camundaLogPath := filepath.Join(parentDir, "log", "camunda.log")
		err := s.startApplication(camundaCmd, processInfo.Camunda.PidPath, camundaLogPath, stop)
		if err != nil {
			log.Err(err).Msg("Failed to start Camunda process")
			stop()
			return
		}
	}, func() error {
		return health.QueryCamunda(ctx, c8, "Camunda", settings, 24)
	}, stop)
}
