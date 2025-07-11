package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"

	"github.com/camunda/camunda/c8run/internal/health"
	"github.com/camunda/camunda/c8run/internal/processmanagement"
	"github.com/camunda/camunda/c8run/internal/shutdown"
	"github.com/camunda/camunda/c8run/internal/start"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func getC8RunPlatform() types.C8Run {
	if runtime.GOOS == "windows" {
		return &windows.WindowsC8Run{}
	} else if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
		return &unix.UnixC8Run{}
	}
	panic("Unsupported operating system")
}

func validateKeystore(settings types.C8RunSettings, parentDir string) error {
	if settings.Keystore == "" {
		return nil
	}

	if settings.KeystorePassword == "" {
		return fmt.Errorf("you must provide a password with --keystorePassword to unlock your keystore")
	}

	if !strings.HasPrefix(settings.Keystore, "/") {
		settings.Keystore = filepath.Join(parentDir, settings.Keystore)
	}

	return nil
}

func runDockerCommand(composeExtractedFolder string, args ...string) error {
	err := os.Chdir(composeExtractedFolder)
	if err != nil {
		return fmt.Errorf("failed to chdir to %s: %w", composeExtractedFolder, err)
	}

	_, err = exec.LookPath("docker")
	if err != nil {
		return err
	}

	composeCmd := exec.Command("docker", append([]string{"compose"}, args...)...)
	composeCmd.Stdout = os.Stdout
	composeCmd.Stderr = os.Stderr

	err = composeCmd.Run()
	if err != nil {
		return err
	}

	err = os.Chdir("..")
	if err != nil {
		return fmt.Errorf("failed to chdir back: %w", err)
	}

	return nil
}

func usage(exitcode int) {
	fmt.Printf("Usage: %s [command] [options]\nCommands:\n  start\n  stop\n", os.Args[0])
	os.Exit(exitcode)
}

func getBaseCommand() (string, error) {
	if len(os.Args) == 1 {
		usage(1)
	}

	switch os.Args[1] {
	case "start":
		return "start", nil
	case "stop":
		return "stop", nil
	case "-h", "--help":
		usage(0)
	default:
		return "", fmt.Errorf("unsupported operation: %s", os.Args[1])
	}

	return "", nil
}

func handleDockerCommand(settings types.C8RunSettings, baseCommand string, composeExtractedFolder string) error {
	if !settings.Docker {
		return nil
	}

	var err error
	switch baseCommand {
	case "start":
		err = runDockerCommand(composeExtractedFolder, "up", "-d")
		if err != nil {
			return err
		}
		err = health.PrintStatus(settings)
	case "stop":
		err = runDockerCommand(composeExtractedFolder, "down")
	default:
		err = fmt.Errorf("command invalid, only start and stop supported")
	}

	if err != nil {
		return err
	}

	os.Exit(0)
	return nil // This line will never be reached, but it's required to satisfy the function signature
}

func getBaseCommandSettings(baseCommand string) (types.C8RunSettings, error) {
	var settings types.C8RunSettings

	startFlagSet := createStartFlagSet(&settings)
	stopFlagSet := createStopFlagSet(&settings)

	switch baseCommand {
	case "start":
		err := startFlagSet.Parse(os.Args[2:])
		if err != nil {
			return settings, fmt.Errorf("error parsing start argument: %w", err)
		}
	case "stop":
		err := stopFlagSet.Parse(os.Args[2:])
		if err != nil {
			return settings, fmt.Errorf("error parsing stop argument: %w", err)
		}
	}

	return settings, nil
}

func createStartFlagSet(settings *types.C8RunSettings) *flag.FlagSet {
	startFlagSet := flag.NewFlagSet("start", flag.ExitOnError)
	startFlagSet.StringVar(&settings.Config, "config", "", "Applies the specified configuration file.")
	startFlagSet.BoolVar(&settings.Detached, "detached", false, "Starts Camunda Run as a detached process")
	startFlagSet.IntVar(&settings.Port, "port", 8088, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.Keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.KeystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")
	startFlagSet.StringVar(&settings.LogLevel, "log-level", "", "Adjust the log level of Camunda")
	startFlagSet.BoolVar(&settings.DisableElasticsearch, "disable-elasticsearch", false, "Do not start or stop Elasticsearch (still requires Elasticsearch to be running outside of c8run)")
	startFlagSet.BoolVar(&settings.Docker, "docker", false, "Run Camunda from docker-compose.")
	startFlagSet.StringVar(&settings.Username, "username", "demo", "Change the first users username (default: demo)")
	startFlagSet.StringVar(&settings.Password, "password", "demo", "Change the first users password (default: demo)")
	startFlagSet.StringVar(&settings.StartupUrl, "startup-url", createOperateUrl(settings), "The URL to open after startup.")
	return startFlagSet
}

func createOperateUrl(settings *types.C8RunSettings) string {
	return fmt.Sprintf("%s://localhost:%s/operate", settings.GetProtocol(), strconv.Itoa(settings.Port))
}

func createStopFlagSet(settings *types.C8RunSettings) *flag.FlagSet {
	stopFlagSet := flag.NewFlagSet("stop", flag.ExitOnError)
	stopFlagSet.BoolVar(&settings.DisableElasticsearch, "disable-elasticsearch", false, "Do not stop Elasticsearch")
	stopFlagSet.BoolVar(&settings.Docker, "docker", false, "Stop docker-compose distribution of camunda.")
	return stopFlagSet
}

func initialize(baseCommand string, baseDir string) *types.State {
	err := godotenv.Load()
	if err != nil {
		fmt.Println(err.Error())
	}

	elasticsearchVersion := os.Getenv("ELASTICSEARCH_VERSION")
	camundaVersion := os.Getenv("CAMUNDA_VERSION")
	connectorsVersion := os.Getenv("CONNECTORS_VERSION")
	composeExtractedFolder := os.Getenv("COMPOSE_EXTRACTED_FOLDER")

	elasticsearchPidPath := filepath.Join(baseDir, "elasticsearch.process")
	connectorsPidPath := filepath.Join(baseDir, "connectors.process")
	camundaPidPath := filepath.Join(baseDir, "camunda.process")

	settings, err := getBaseCommandSettings(baseCommand)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	if settings.LogLevel != "" {
		os.Setenv("ZEEBE_LOG_LEVEL", settings.LogLevel)
	}

	err = validateKeystore(settings, baseDir)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	err = handleDockerCommand(settings, baseCommand, composeExtractedFolder)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	processInfo := types.Processes{
		Camunda: types.Process{
			Version: camundaVersion,
			PidPath: camundaPidPath,
		},
		Connectors: types.Process{
			Version: connectorsVersion,
			PidPath: connectorsPidPath,
		},
		Elasticsearch: types.Process{
			Version: elasticsearchVersion,
			PidPath: elasticsearchPidPath,
		},
	}

	return &types.State{
		C8:          getC8RunPlatform(),
		Settings:    settings,
		ProcessInfo: processInfo,
	}
}

func main() {
	consoleWriter := zerolog.ConsoleWriter{Out: os.Stderr}
	logger := zerolog.New(consoleWriter).With().Timestamp().Logger()
	if os.Getenv("C8RUN_DEBUG_WITH_LINE_NUMBERS") != "" {
		logger = logger.With().Caller().Logger()
	}
	log.Logger = logger

	baseCommand, err := getBaseCommand()
	if err != nil {
		log.Err(err).Msg("There is an issue with getting the base command")
		os.Exit(1)
	}

	baseDir, _ := os.Getwd()
	state := initialize(baseCommand, baseDir)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGKILL, syscall.SIGINT, syscall.SIGTERM)

	var wg sync.WaitGroup
	wg.Add(1)
	workDone := make(chan struct{})
	sh := &shutdown.ShutdownHandler{
		ProcessHandler: &processmanagement.ProcessHandler{
			C8: state.C8,
		},
	}
	startupHandler := &start.StartupHandler{
		ProcessHandler: &processmanagement.ProcessHandler{
			C8: state.C8,
		},
	}
	switch baseCommand {
	case "start":
		go func() {
			// TODO make a lock file to prevent zombie processes if start is called with &
			startupHandler.StartCommand(&wg, ctx, stop, state, baseDir)
			close(workDone)
		}()
	case "stop":
		go func() {
			sh.ShutdownProcesses(state)
			close(workDone)
		}()
	}

	select {
	case <-workDone:
		log.Info().Msg("All processes are running and healthy, exiting...")
	case <-ctx.Done():
		log.Info().Msg("Received shutdown signal, stopping all workers...")
		sh.ShutdownProcesses(state)
		wg.Wait()
		log.Info().Msg("All workers have stopped. Application has been gracefully shut down.")
	}
}
