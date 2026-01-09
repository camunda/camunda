package main

import (
	"bytes"
	"context"
	"errors"
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
	"gopkg.in/yaml.v3"
)

func getC8RunPlatform() types.C8Run {
	switch runtime.GOOS {
	case "windows":
		return &windows.WindowsC8Run{}
	case "linux", "darwin":
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

func validatePort(port int) error {
	if port < 1 || port > 65535 {
		return fmt.Errorf("--port must be between 1 and 65535 (got %d)", port)
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

var helpTemplate = `Usage:
  %[1]s [command] [options]

Commands:
  start                 Start Camunda 8 Run
  stop                  Stop any running Camunda 8 Run processes
  help                  Show this help message

Options:
  --config <path>           Use a custom Zeebe application.yaml
  --keystore <path>         Enable HTTPS with a TLS certificate (JKS format)
  --keystorePassword <pw>  Password for the provided keystore
  --port <number>           Set the main Camunda port (default: 8080)
  --log-level <level>       Set log level (e.g., info, debug)
  --docker                  Start using Docker Compose

Examples:
  %[1]s start
  %[1]s start --docker
  %[1]s start --config ./my-config.yaml
  %[1]s stop
  %[1]s stop --docker

Docs & Support:
  https://docs.camunda.io/docs/guides/getting-started-java-spring/
  https://docs.camunda.io/docs/next/guides/getting-started-agentic-orchestration/
  https://forum.camunda.io
`

func usage(exitcode int) {
	fmt.Printf(helpTemplate, os.Args[0])
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
	case "help":
		usage(0)
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

const docsStartupURL = "https://docs.camunda.io/docs/next/self-managed/quickstart/developer-quickstart/c8run/#work-with-camunda-8-run"

func getBaseCommandSettings(baseCommand string) (types.C8RunSettings, bool, error) {
	var (
		settings           types.C8RunSettings
		startupURLProvided bool
	)

	startFlagSet := createStartFlagSet(&settings)
	stopFlagSet := createStopFlagSet(&settings)

	switch baseCommand {
	case "start":
		err := startFlagSet.Parse(os.Args[2:])
		if err != nil {
			return settings, startupURLProvided, fmt.Errorf("error parsing start argument: %w", err)
		}
		startupURLProvided = flagPassed(startFlagSet, "startup-url")
		if err := validatePort(settings.Port); err != nil {
			return settings, startupURLProvided, err
		}
	case "stop":
		err := stopFlagSet.Parse(os.Args[2:])
		if err != nil {
			return settings, startupURLProvided, fmt.Errorf("error parsing stop argument: %w", err)
		}
	}

	return settings, startupURLProvided, nil
}

// flagPassed determines if a flag was explicitly set by the user.
func flagPassed(fs *flag.FlagSet, name string) bool {
	found := false
	fs.Visit(func(f *flag.Flag) {
		if f.Name == name {
			found = true
		}
	})
	return found
}

func createStartFlagSet(settings *types.C8RunSettings) *flag.FlagSet {
	startFlagSet := flag.NewFlagSet("start", flag.ExitOnError)
	startFlagSet.StringVar(&settings.Config, "config", "", "Applies the specified configuration file.")
	startFlagSet.BoolVar(&settings.Detached, "detached", false, "Starts Camunda Run as a detached process")
	startFlagSet.IntVar(&settings.Port, "port", 8080, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.Keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.KeystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")
	startFlagSet.StringVar(&settings.LogLevel, "log-level", "", "Adjust the log level of Camunda")
	startFlagSet.BoolVar(&settings.DisableElasticsearch, "disable-elasticsearch", true, "Skip managing Elasticsearch (default). Set to false (and configure the application) to run with Elasticsearch instead of H2.")
	startFlagSet.BoolVar(&settings.Docker, "docker", false, "Run Camunda from docker-compose.")
	startFlagSet.StringVar(&settings.Username, "username", "demo", "Change the first users username (default: demo)")
	startFlagSet.StringVar(&settings.Password, "password", "demo", "Change the first users password (default: demo)")
	startFlagSet.StringVar(&settings.StartupUrl, "startup-url", "", "The URL to open after startup.")
	return startFlagSet
}

func createOperateUrl(settings *types.C8RunSettings) string {
	return fmt.Sprintf("%s://localhost:%s/operate", settings.GetProtocol(), strconv.Itoa(settings.Port))
}

func createDefaultStartupUrl(settings *types.C8RunSettings, camundaVersion string) string {
	if shouldUseDocsStartup(camundaVersion) {
		return docsStartupURL
	}
	return createOperateUrl(settings)
}

func shouldUseDocsStartup(camundaVersion string) bool {
	major, minor, ok := parseMajorMinor(camundaVersion)
	if !ok {
		return false
	}
	if major > 8 {
		return true
	}
	return major == 8 && minor >= 9
}

func parseMajorMinor(version string) (int, int, bool) {
	version = strings.TrimSpace(version)
	if version == "" {
		return 0, 0, false
	}
	base := strings.SplitN(version, "-", 2)[0]
	parts := strings.Split(base, ".")
	if len(parts) < 2 {
		return 0, 0, false
	}
	major, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, 0, false
	}
	minor, err := strconv.Atoi(parts[1])
	if err != nil {
		return 0, 0, false
	}
	return major, minor, true
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

	settings, startupURLProvided, err := getBaseCommandSettings(baseCommand)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	applySecondaryStorageDefaults(baseDir, &settings)

	if !startupURLProvided {
		settings.StartupUrl = createDefaultStartupUrl(&settings, camundaVersion)
	}

	if settings.LogLevel != "" {
		if err := os.Setenv("ZEEBE_LOG_LEVEL", settings.LogLevel); err != nil {
			log.Error().Err(err).Msg("failed to set ZEEBE_LOG_LEVEL log level")
		}
	}

	err = validateKeystore(settings, baseDir)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	camundaVersion, connectorsVersion = applyDockerVersionOverrides(settings, camundaVersion, connectorsVersion)
	propagateRuntimeVersions(elasticsearchVersion, camundaVersion, connectorsVersion)

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

func applySecondaryStorageDefaults(baseDir string, settings *types.C8RunSettings) {
	configPaths := resolveConfigPaths(baseDir, settings.Config)

	var secondaryType string
	var configSource string
	for _, path := range configPaths {
		// We only expect one active config; use the first file where the type is defined
		typeFromConfig, err := detectSecondaryStorageType(path)
		if err != nil {
			log.Debug().Err(err).Str("config", path).Msg("Unable to read configuration for secondary storage type")
			continue
		}
		if typeFromConfig != "" {
			secondaryType = typeFromConfig
			configSource = path
			break
		}
	}

	settings.SecondaryStorageType = strings.TrimSpace(secondaryType)
	if settings.SecondaryStorageType == "" {
		// Nothing configured, keep whatever defaults were provided via CLI/env
		return
	}

	// Any non-elasticsearch backend means we keep Elasticsearch disabled (default true)
	if !strings.EqualFold(settings.SecondaryStorageType, "elasticsearch") {
		settings.DisableElasticsearch = true
		event := log.Info().
			Str("secondaryStorage.type", settings.SecondaryStorageType)
		if configSource != "" {
			event = event.Str("config", configSource)
		}
		event.Msg("Secondary storage type is not Elasticsearch; Elasticsearch processes will be skipped")
		return
	}

	event := log.Debug().
		Str("secondaryStorage.type", settings.SecondaryStorageType)
	if configSource != "" {
		event = event.Str("config", configSource)
	}
	event.Msg("Secondary storage type is Elasticsearch; keeping default behavior")
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

func detectSecondaryStorageType(path string) (string, error) {
	info, err := os.Stat(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return "", nil
		}
		return "", err
	}
	if info.IsDir() {
		return detectSecondaryStorageType(filepath.Join(path, "application.yaml"))
	}

	content, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return "", nil
		}
		return "", err
	}

	ext := strings.ToLower(filepath.Ext(path))
	if ext != ".yaml" && ext != ".yml" {
		return "", nil
	}
	return parseSecondaryStorageTypeFromYAML(content)
}

func parseSecondaryStorageTypeFromYAML(content []byte) (string, error) {
	if len(bytes.TrimSpace(content)) == 0 {
		return "", nil
	}

	var root map[string]any
	if err := yaml.Unmarshal(content, &root); err != nil {
		return "", err
	}
	return extractSecondaryStorageTypeFromMap(root), nil
}

func extractSecondaryStorageTypeFromMap(root map[string]any) string {
	camunda, ok := root["camunda"].(map[string]any)
	if !ok {
		return ""
	}
	data, ok := camunda["data"].(map[string]any)
	if !ok {
		return ""
	}
	secondary, ok := data["secondary-storage"].(map[string]any)
	if !ok {
		return ""
	}
	if typ, ok := secondary["type"].(string); ok {
		return typ
	}
	return ""
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
	shutdownWorkDone := make(chan struct{})
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
			close(shutdownWorkDone)
		}()
	}

	select {
	case <-workDone:
		log.Info().Msg("All processes are running and healthy, exiting script...")
	case <-shutdownWorkDone:
		log.Info().Msg("All processes have been shut down, exiting script...")
	case <-ctx.Done():
		log.Info().Msg("Received shutdown signal, stopping all workers...")
		sh.ShutdownProcesses(state)
		wg.Wait()
		log.Info().Msg("All workers have stopped. Application has been gracefully shut down.")
	}
}

func applyDockerVersionOverrides(settings types.C8RunSettings, camundaVersion string, connectorsVersion string) (string, string) {
	if !settings.Docker {
		return camundaVersion, connectorsVersion
	}

	if dockerCamunda := strings.TrimSpace(os.Getenv("CAMUNDA_DOCKER_VERSION")); dockerCamunda != "" {
		camundaVersion = dockerCamunda
	}

	if dockerConnectors := strings.TrimSpace(os.Getenv("CONNECTORS_DOCKER_VERSION")); dockerConnectors != "" {
		connectorsVersion = dockerConnectors
	}

	return camundaVersion, connectorsVersion
}

func propagateRuntimeVersions(elasticsearchVersion, camundaVersion, connectorsVersion string) {
	if elasticsearchVersion != "" {
		if err := os.Setenv("ELASTIC_VERSION", elasticsearchVersion); err != nil {
			log.Error().Err(err).Msg("failed to update ELASTIC_VERSION environment variable")
		}
	}

	if camundaVersion != "" {
		if err := os.Setenv("CAMUNDA_VERSION", camundaVersion); err != nil {
			log.Error().Err(err).Msg("failed to update CAMUNDA_VERSION environment variable")
		}
	}

	if connectorsVersion == "" {
		return
	}

	for _, key := range []string{"CONNECTORS_VERSION", "CAMUNDA_CONNECTORS_VERSION"} {
		if err := os.Setenv(key, connectorsVersion); err != nil {
			log.Error().Err(err).Str("key", key).Msg("failed to update connectors environment variable")
		}
	}
}
