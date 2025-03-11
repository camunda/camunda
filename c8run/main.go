package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/health"
	"github.com/camunda/camunda/c8run/internal/overrides"
	"github.com/camunda/camunda/c8run/internal/packages"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
)

func stopProcess(c8 types.C8Run, pidfile string) error {
	if _, err := os.Stat(pidfile); err == nil {
		commandPidText, _ := os.ReadFile(pidfile)
		commandPidStripped := strings.TrimSpace(string(commandPidText))
		commandPid, err := strconv.Atoi(string(commandPidStripped))
		if err != nil {
			return fmt.Errorf("stopProcess: could not stop process %d, %w", commandPid, err)
		}

		for _, process := range c8.ProcessTree(int(commandPid)) {
			err = process.Kill()
			if err != nil {
				return fmt.Errorf("stopProcess: could not kill process %d, %w", commandPid, err)
			}
		}
		os.Remove(pidfile)

	}
	return nil
}

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
	fmt.Printf("Usage: %s [command] [options]\nCommands:\n  start\n  stop\n  package\n", os.Args[0])
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
	case "package":
		return "package", nil
	case "clean":
		return "clean", nil
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
	case "stop":
		err = runDockerCommand(composeExtractedFolder, "down")
	default:
		err = fmt.Errorf("No valid command. Only start and stop supported.")
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
	startFlagSet.IntVar(&settings.Port, "port", 8080, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.Keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.KeystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")
	startFlagSet.StringVar(&settings.LogLevel, "log-level", "", "Adjust the log level of Camunda")
	startFlagSet.BoolVar(&settings.DisableElasticsearch, "disable-elasticsearch", false, "Do not start or stop Elasticsearch (still requires Elasticsearch to be running outside of c8run)")
	startFlagSet.BoolVar(&settings.Docker, "docker", false, "Run Camunda from docker-compose.")
	startFlagSet.StringVar(&settings.Username, "username", "demo", "Change the first users username (default: demo)")
	startFlagSet.StringVar(&settings.Password, "password", "demo", "Change the first users password (default: demo)")
	return startFlagSet
}

func createStopFlagSet(settings *types.C8RunSettings) *flag.FlagSet {
	stopFlagSet := flag.NewFlagSet("stop", flag.ExitOnError)
	stopFlagSet.BoolVar(&settings.DisableElasticsearch, "disable-elasticsearch", false, "Do not stop Elasticsearch")
	stopFlagSet.BoolVar(&settings.Docker, "docker", false, "Stop docker-compose distribution of camunda.")
	return stopFlagSet
}

func main() {
	c8 := getC8RunPlatform()
	baseDir, _ := os.Getwd()
	parentDir := baseDir

	err := godotenv.Load()
	if err != nil {
		fmt.Println(err.Error())
	}

	elasticsearchVersion := os.Getenv("ELASTICSEARCH_VERSION")
	camundaVersion := os.Getenv("CAMUNDA_VERSION")
	connectorsVersion := os.Getenv("CONNECTORS_VERSION")
	composeTag := os.Getenv("COMPOSE_TAG")
	composeExtractedFolder := os.Getenv("COMPOSE_EXTRACTED_FOLDER")

	expectedJavaVersion := 21

	elasticsearchPidPath := filepath.Join(baseDir, "elasticsearch.process")
	connectorsPidPath := filepath.Join(baseDir, "connectors.process")
	camundaPidPath := filepath.Join(baseDir, "camunda.process")

	baseCommand, err := getBaseCommand()
	if err != nil {
		fmt.Println(err.Error())
	}

	settings, err := getBaseCommandSettings(baseCommand)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	if settings.LogLevel != "" {
		os.Setenv("ZEEBE_LOG_LEVEL", settings.LogLevel)
	}

	err = validateKeystore(settings, parentDir)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	err = handleDockerCommand(settings, baseCommand, composeExtractedFolder)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	javaHome := os.Getenv("JAVA_HOME")
	javaBinary := "java"
	javaHomeAfterSymlink, err := filepath.EvalSymlinks(javaHome)
	if err != nil {
		fmt.Println("Failed to check if filepath is a symlink")
		os.Exit(1)
	}
	javaHome = javaHomeAfterSymlink
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
			fmt.Println(err.Error())
			os.Exit(1)
		}
		// fallback to bin/java.exe
		if javaBinary == "" {
			if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
				javaBinary = filepath.Join(javaHome, "bin", "java")
			} else if runtime.GOOS == "windows" {
				javaBinary = filepath.Join(javaHome, "bin", "java.exe")
			}
		}
	} else {
		path, err := exec.LookPath("java")
		if err != nil {
			fmt.Println("Failed to find JAVA_HOME or java program.")
			os.Exit(1)
		}

		// go up 2 directories since it's not guaranteed that java is in a bin folder
		javaHome = filepath.Dir(filepath.Dir(path))
		javaBinary = path
	}

	err = overrides.SetEnvVars(javaHome)
	if err != nil {
		fmt.Println("Failed to set envVars:", err)
	}

	processInfo := processes{
		camunda: process{
			version: camundaVersion,
			pid:     camundaPidPath,
		},
		connectors: process{
			version: connectorsVersion,
			pid:     connectorsPidPath,
		},
		elasticsearch: process{
			version: elasticsearchVersion,
			pid:     elasticsearchPidPath,
		},
	}

	switch baseCommand {
	case "start":
		startCommand(c8, settings, processInfo, parentDir, javaBinary, expectedJavaVersion)
	case "stop":
		stopCommand(c8, settings, processInfo)
	case "package":
		err := packages.New(camundaVersion, elasticsearchVersion, connectorsVersion, composeTag)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	case "clean":
		cleanCommand(camundaVersion, elasticsearchVersion)
	}

}

func startCommand(c8 types.C8Run, settings types.C8RunSettings, processInfo processes, parentDir, javaBinary string, expectedJavaVersion int) {
	javaVersion := os.Getenv("JAVA_VERSION")
	if javaVersion == "" {
		javaVersionCmd := c8.VersionCmd(javaBinary)
		var out strings.Builder
		var stderr strings.Builder
		javaVersionCmd.Stdout = &out
		javaVersionCmd.Stderr = &stderr
		err := javaVersionCmd.Run()
		if err != nil {
			fmt.Println(err)
			os.Exit(1)
		}
		javaVersionOutput := out.String()
		javaVersionOutputSplit := strings.Split(javaVersionOutput, " ")
		if len(javaVersionOutputSplit) < 2 {
			fmt.Println("Java needs to be installed. Please install JDK " + strconv.Itoa(expectedJavaVersion) + " or newer.")
			fmt.Println("If java is already installed, try explicitly setting JAVA_HOME and JAVA_VERSION")
			os.Exit(1)
		}
		output := javaVersionOutputSplit[1]
		os.Setenv("JAVA_VERSION", output)
		javaVersion = output
	}
	fmt.Print("Java version is " + javaVersion + "\n")

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

	if !settings.DisableElasticsearch {
		fmt.Print("Starting Elasticsearch " + processInfo.elasticsearch.version + "...\n")
		fmt.Print("(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)\n")

		elasticsearchCmd := c8.ElasticsearchCmd(processInfo.elasticsearch.version, parentDir)
		elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
		startApplication(elasticsearchCmd, processInfo.elasticsearch.pid, elasticsearchLogFilePath)
		health.QueryElasticsearch("Elasticsearch", "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s")
	}

	connectorsCmd := c8.ConnectorsCmd(javaBinary, parentDir, processInfo.camunda.version)
	connectorsLogPath := filepath.Join(parentDir, "log", "connectors.log")
	startApplication(connectorsCmd, processInfo.connectors.pid, connectorsLogPath)

	var extraArgs string
	if settings.Config != "" {
		path := filepath.Join(parentDir, settings.Config)
		var slash string
		if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
			slash = "/"
		} else if runtime.GOOS == "windows" {
			slash = "\\"
		}

		configStat, err := os.Stat(path)
		if err != nil {
			fmt.Printf("Failed to read config file: %s\n", path)
			os.Exit(1)
		}
		if configStat.IsDir() {
			extraArgs = "--spring.config.additional-location=file:" + filepath.Join(parentDir, settings.Config) + slash
		} else {
			extraArgs = "--spring.config.additional-location=file:" + filepath.Join(parentDir, settings.Config)
		}
	}

	camundaCmd := c8.CamundaCmd(processInfo.camunda.version, parentDir, extraArgs, javaOpts)
	camundaLogPath := filepath.Join(parentDir, "log", "camunda.log")
	startApplication(camundaCmd, processInfo.camunda.pid, camundaLogPath)
	err := health.QueryCamunda(c8, "Camunda", settings)
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

}

type processes struct {
	camunda       process
	connectors    process
	elasticsearch process
}

type process struct {
	version string
	pid     string
}

func stopCommand(c8 types.C8Run, settings types.C8RunSettings, processes processes) {
	if !settings.DisableElasticsearch {
		err := stopProcess(c8, processes.elasticsearch.pid)
		if err != nil {
			fmt.Printf("%+v", err)
		}
		fmt.Println("Elasticsearch is stopped.")
	}
	err := stopProcess(c8, processes.connectors.pid)
	if err != nil {
		fmt.Printf("%+v", err)
	}
	fmt.Println("Connectors is stopped.")
	err = stopProcess(c8, processes.camunda.pid)
	if err != nil {
		fmt.Printf("%+v", err)
	}
	fmt.Println("Camunda is stopped.")
}

func cleanCommand(camundaVersion string, elasticsearchVersion string) {
	packages.Clean(camundaVersion, elasticsearchVersion)
}

func startApplication(cmd *exec.Cmd, pid string, logPath string) {
	logFile, err := os.OpenFile(logPath, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		fmt.Print("Failed to open file: " + logPath)
		os.Exit(1)
	}
	cmd.Stdout = logFile
	cmd.Stderr = logFile
	err = cmd.Start()
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

	pidFile, err := os.OpenFile(pid, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		fmt.Print("Failed to open file: " + pid)
		os.Exit(1)
	}
	_, err = pidFile.Write([]byte(strconv.Itoa(cmd.Process.Pid)))
	if err != nil {
		fmt.Print("Failed to write to file: " + pid + " continuing...")
	}
}
