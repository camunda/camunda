package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"syscall"

	"github.com/camunda/camunda/c8run/internal/physicaltenants"
	"github.com/camunda/camunda/c8run/internal/processmanagement"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/camunda/camunda/c8run/internal/shutdown"
	"github.com/camunda/camunda/c8run/internal/springconfig"
	"github.com/camunda/camunda/c8run/internal/start"
	"github.com/camunda/camunda/c8run/internal/startupurl"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
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

var helpTemplate = `Usage:
  %[1]s [command] [options]

Commands:
  start                 Start Camunda 8 Run
  stop                  Stop any running Camunda 8 Run processes
  secrets               Manage local development secrets
  tenants               Manage physical tenants (isolated engines in one c8run)
  help                  Show this help message

Options:
  --config <path>           Use a custom Zeebe application.yaml
  --disable-connectors      Start Camunda 8 Run without the bundled connectors runtime
  --extra-driver <path>     Copy a JDBC driver into the Camunda lib directory before startup (repeat per jar)
  --keystore <path>         Enable HTTPS with a TLS certificate (JKS format)
  --keystorePassword <pw>  Password for the provided keystore
  --no-browser              Start Camunda 8 Run without opening a browser window
  --port <number>           Set the main Camunda port (default: 8080)
  --log-level <level>       Set log level (e.g., info, debug)
  --physical-tenants <ids>  Start extra physical tenants for this run only (e.g. sales,hr)

Examples:
  %[1]s start
  %[1]s start --disable-connectors
  %[1]s start --no-browser
  %[1]s start --config ./my-config.yaml
  %[1]s stop
  %[1]s secrets set OPENAI_API_KEY
  %[1]s secrets import .env.secrets
  %[1]s tenants add sales
  %[1]s start --physical-tenants sales,hr

Docs & Support:
  https://docs.camunda.io/docs/guides/getting-started-java-spring/
  https://docs.camunda.io/docs/next/guides/getting-started-agentic-orchestration/
  https://forum.camunda.io
`

func usage(exitcode int) {
	fmt.Print(usageText(os.Args[0], cliName()))
	os.Exit(exitcode)
}

// usageText renders the help template, preferring a wrapper's configured CLI
// name over the executable path.
func usageText(executable, name string) string {
	if name == "" {
		name = executable
	}
	return fmt.Sprintf(helpTemplate, name)
}

// exitWithError prints err with c8run commands rewritten for a wrapper and exits.
func exitWithError(err error) {
	fmt.Println(withCLIName(err.Error(), cliName()))
	os.Exit(1)
}

type stringSliceFlag []string

func (s *stringSliceFlag) String() string {
	return strings.Join(*s, ",")
}

func (s *stringSliceFlag) Set(value string) error {
	*s = append(*s, value)
	return nil
}

func getBaseCommand() (string, error) {
	if len(os.Args) == 1 {
		usage(0)
	}

	switch os.Args[1] {
	case "start":
		return "start", nil
	case "stop":
		return "stop", nil
	case "secrets":
		return "secrets", nil
	case "tenants", "physical-tenants", "pt":
		return "tenants", nil
	case "help":
		usage(0)
	case "-h", "--help":
		usage(0)
	default:
		return "", fmt.Errorf("unsupported operation: %s", os.Args[1])
	}

	return "", nil
}

const docsStartupURL = startupurl.DocsURL

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
		ids, err := physicaltenants.ParseIDList(settings.PhysicalTenantsFlag)
		if err != nil {
			return settings, startupURLProvided, fmt.Errorf("--physical-tenants: %w", err)
		}
		if flagPassed(startFlagSet, "physical-tenants") && len(ids) == 0 {
			return settings, startupURLProvided, errors.New("--physical-tenants needs at least one tenant ID (e.g. --physical-tenants sales,hr); omit it to start your saved tenants")
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
	startFlagSet.BoolVar(&settings.DisableConnectors, "disable-connectors", false, "Skips starting the bundled connectors runtime.")
	startFlagSet.BoolVar(&settings.NoBrowser, "no-browser", false, "Skips opening a browser window after startup.")
	startFlagSet.Var((*stringSliceFlag)(&settings.ExtraDrivers), "extra-driver", "Path to a JDBC driver jar to copy into the Camunda lib directory (repeatable).")
	startFlagSet.BoolVar(&settings.Detached, "detached", false, "Starts Camunda Run as a detached process")
	startFlagSet.IntVar(&settings.Port, "port", 8080, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.Keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.KeystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")
	startFlagSet.StringVar(&settings.LogLevel, "log-level", "", "Adjust the log level of Camunda")
	startFlagSet.StringVar(&settings.Username, "username", "demo", "Change the first users username (default: demo)")
	startFlagSet.StringVar(&settings.Password, "password", "demo", "Change the first users password (default: demo)")
	startFlagSet.StringVar(&settings.StartupUrl, "startup-url", "", "The URL to open after startup.")
	startFlagSet.Var((*stringSliceFlag)(&settings.PhysicalTenantsFlag), "physical-tenants", "Comma-separated physical tenant IDs to start for this run only (repeatable).")
	return startFlagSet
}

func createDefaultStartupUrl(settings *types.C8RunSettings, camundaVersion string) string {
	return startupurl.Default(*settings, camundaVersion)
}

func createStopFlagSet(settings *types.C8RunSettings) *flag.FlagSet {
	stopFlagSet := flag.NewFlagSet("stop", flag.ExitOnError)
	return stopFlagSet
}

func initialize(baseCommand string, baseDir string) *types.State {
	camundaVersion := os.Getenv("CAMUNDA_VERSION")
	connectorsVersion := os.Getenv("CONNECTORS_VERSION")

	connectorsPidPath := filepath.Join(baseDir, "connectors.process")
	camundaPidPath := filepath.Join(baseDir, "camunda.process")

	settings, startupURLProvided, err := getBaseCommandSettings(baseCommand)
	if err != nil {
		exitWithError(err)
	}

	applyConfigSettings(baseDir, &settings)
	if baseCommand == "start" {
		if err := applyPhysicalTenants(baseDir, camundaVersion, &settings); err != nil {
			exitWithError(err)
		}
	}
	settings.StartupMarkerPath = startupurl.MarkerPath(baseDir)

	if strings.EqualFold(settings.SecondaryStorageType, "rdbms") {
		url, _ := springconfig.Value(settings.ConfigPaths, "camunda.data.secondary-storage.rdbms.url")
		if err := ensureDriversAvailable(baseDir, camundaVersion, rdbmsVendorFromURL(url), settings.ExtraDrivers); err != nil {
			exitWithError(err)
		}
	}

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
		exitWithError(err)
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
	}

	return &types.State{
		C8:          getC8RunPlatform(),
		Settings:    settings,
		ProcessInfo: processInfo,
	}
}

func applyConfigSettings(baseDir string, settings *types.C8RunSettings) {
	settings.ConfigPaths = springconfig.Paths(baseDir, settings.Config)
	settings.SecondaryStorageType, _ = springconfig.Value(settings.ConfigPaths, "camunda.data.secondary-storage.type")
	method, _ := springconfig.Value(settings.ConfigPaths, "camunda.security.authentication.method")
	settings.OIDC = strings.EqualFold(method, "oidc")
	log.Debug().Str("secondaryStorage.type", settings.SecondaryStorageType).Msg("Resolved secondary storage type from configuration")
}

func rdbmsVendorFromURL(url string) string {
	url = strings.ToLower(strings.TrimSpace(url))
	switch {
	case strings.HasPrefix(url, "jdbc:oracle:"):
		return "oracle"
	case strings.HasPrefix(url, "jdbc:postgresql:"):
		return "postgresql"
	case strings.HasPrefix(url, "jdbc:mariadb:"):
		return "mariadb"
	case strings.HasPrefix(url, "jdbc:mysql:"):
		return "mysql"
	case strings.HasPrefix(url, "jdbc:sqlserver:"):
		return "mssql"
	}
	return ""
}

var externalDriverPatterns = map[string][]string{
	"oracle": {"ojdbc*.jar"},
	"mysql":  {"mysql-connector-java-*.jar", "mysql-connector-j-*.jar"},
}

func ensureDriversAvailable(baseDir, camundaVersion, vendor string, extraDrivers []string) error {
	if camundaVersion == "" {
		if len(extraDrivers) == 0 && !needsExternalDriver(vendor) {
			return nil
		}
		return fmt.Errorf("CAMUNDA_VERSION is not set; unable to determine lib directory for JDBC drivers")
	}

	libDir := filepath.Join(baseDir, fmt.Sprintf("camunda-zeebe-%s", camundaVersion), "lib")
	if _, err := os.Stat(libDir); err != nil {
		return fmt.Errorf("unable to locate Camunda lib directory (%s): %w", libDir, err)
	}

	for _, src := range extraDrivers {
		dest := filepath.Join(libDir, filepath.Base(src))
		if err := copyFile(src, dest); err != nil {
			return fmt.Errorf("failed to copy JDBC driver %s: %w", src, err)
		}
		log.Info().Str("source", src).Str("destination", dest).Msg("Copied extra JDBC driver")
	}

	if needsExternalDriver(vendor) && !driverPresent(libDir, vendor) {
		return fmt.Errorf("JDBC driver for %s not found in %s. Download it and re-run with --extra-driver <path-to-jar>", vendor, libDir)
	}

	return nil
}

func needsExternalDriver(vendor string) bool {
	_, ok := externalDriverPatterns[vendor]
	return ok
}

func driverPresent(libDir, vendor string) bool {
	patterns, ok := externalDriverPatterns[vendor]
	if !ok {
		return true
	}
	for _, pattern := range patterns {
		matches, err := filepath.Glob(filepath.Join(libDir, pattern))
		if err != nil {
			continue
		}
		if len(matches) > 0 {
			return true
		}
	}
	return false
}

func copyFile(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer func() {
		if cerr := srcFile.Close(); cerr != nil {
			log.Err(cerr).Str("path", src).Msg("Failed to close source file")
		}
	}()

	destFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer func() {
		if cerr := destFile.Close(); cerr != nil {
			log.Err(cerr).Str("path", dst).Msg("Failed to close destination file")
		}
	}()

	if _, err := io.Copy(destFile, srcFile); err != nil {
		return err
	}
	return nil
}

func main() {
	consoleWriter := zerolog.ConsoleWriter{Out: os.Stderr}
	logger := zerolog.New(consoleWriter).With().Timestamp().Logger()
	if os.Getenv("C8RUN_DEBUG_WITH_LINE_NUMBERS") != "" {
		logger = logger.With().Caller().Logger()
	}
	log.Logger = logger
	_ = godotenv.Load()

	baseCommand, err := getBaseCommand()
	if err != nil {
		// Plain text: wrappers (c8ctl) match this message to suggest an upgrade.
		fmt.Fprintln(os.Stderr, withCLIName(err.Error()+" (run `c8run help` for usage)", cliName()))
		os.Exit(1)
	}

	baseDir, _ := os.Getwd()
	if baseCommand == "tenants" {
		if err := newTenantsCommand().run(baseDir, os.Args[2:]); err != nil {
			fmt.Fprintln(os.Stderr, withCLIName(err.Error(), cliName()))
			os.Exit(1)
		}
		return
	}
	if baseCommand == "secrets" {
		if err := newSecretsCommand().run(baseDir, os.Args[2:]); err != nil {
			fmt.Fprintln(os.Stderr, withCLIName(err.Error(), cliName()))
			os.Exit(1)
		}
		return
	}
	state := initialize(baseCommand, baseDir)
	if baseCommand == "start" {
		mode, err := localsecrets.Mode()
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		if mode != "local" && len(state.Settings.PhysicalTenants) > 0 {
			fmt.Fprintf(os.Stderr, "Physical tenants managed by c8run need their own secret stores, which c8run only sets up when %s=local.\n"+
				"Either unset %s, or declare the tenants and their secret stores in your --config "+
				"(camunda.physical-tenants.<id>.secrets.stores.*).\n", localsecrets.ModeEnv, localsecrets.ModeEnv)
			os.Exit(1)
		}
		if mode == "local" {
			secretDirectory, err := localsecrets.ResolveDirectory(baseDir)
			if err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
			if err := os.Setenv(localsecrets.DirectoryEnv, secretDirectory); err != nil {
				fmt.Fprintln(os.Stderr, "failed to configure local secrets directory")
				os.Exit(1)
			}
			if err := localsecrets.New(baseDir).Ensure(); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
			if err := os.Setenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH", secretDirectory); err != nil {
				fmt.Fprintln(os.Stderr, "failed to configure Camunda secret store")
				os.Exit(1)
			}
			if err := configureTenantSecretStores(baseDir, &state.Settings); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
		}
	}

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
		if len(state.NotReadyTenants) > 0 {
			fmt.Fprint(os.Stderr, withCLIName(fmt.Sprintf("\nCamunda is running, but physical tenant(s) %s did not become ready.\n"+
				"Check log/camunda.log for physical tenant errors, fix them, then run `./c8run stop && ./c8run start`.\n",
				strings.Join(state.NotReadyTenants, ", ")), cliName()))
			os.Exit(1)
		}
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
