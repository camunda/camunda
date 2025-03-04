package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/health"
	"github.com/camunda/camunda/c8run/internal/packages"
	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
)

type processes struct {
	camunda       process
	connectors    process
	elasticsearch process
}

type process struct {
	version string
	pid     string
}

func stopProcess(c8 C8Run, pidfile string) error {
	if _, err := os.Stat(pidfile); err == nil {
		commandPidText, _ := os.ReadFile(pidfile)
		commandPidStripped := strings.TrimSpace(string(commandPidText))
		commandPid, _ := strconv.Atoi(string(commandPidStripped))

		for _, process := range c8.ProcessTree(int(commandPid)) {
			process.Kill()
		}
		os.Remove(pidfile)
		return nil
	} else if errors.Is(err, os.ErrNotExist) {
		// path/to/whatever does *not* exist
		return nil
	} else {
		// Schrodinger: file may or may not exist. See err for details.
		// Therefore, do *NOT* use !os.IsNotExist(err) to test for file existence
		return err
	}
}

func getC8RunPlatform() C8Run {
	if runtime.GOOS == "windows" {
		return &windows.WindowsC8Run{}
	} else if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
		return &unix.UnixC8Run{}
	}
	panic("Unsupported operating system")
}

func startDocker(extractedComposePath string) error {
	os.Chdir(extractedComposePath)

	_, err := exec.LookPath("docker")
	if err != nil {
		return err
	}

	composeCmd := exec.Command("docker", "compose", "up", "-d")
	composeCmd.Stdout = os.Stdout
	composeCmd.Stderr = os.Stderr
	err = composeCmd.Run()
	if err != nil {
		return err
	}
	os.Chdir("..")
	return nil
}

func stopDocker(extractedComposePath string) error {
	os.Chdir(extractedComposePath)
	_, err := exec.LookPath("docker")
	if err != nil {
		return err
	}
	composeCmd := exec.Command("docker", "compose", "down")
	composeCmd.Stdout = os.Stdout
	composeCmd.Stderr = os.Stderr
	err = composeCmd.Run()
	if err != nil {
		return err
	}
	os.Chdir("..")
	return nil
}

func usage(exitcode int) {
	fmt.Printf("Usage: %s [command] [options]\nCommands:\n  start\n  stop\n  package\n", os.Args[0])
	os.Exit(exitcode)
}

func setEnvVars(javaHome string) error {
	envVars := map[string]string{
		"CAMUNDA_OPERATE_CSRFPREVENTIONENABLED":                  "false",
		"CAMUNDA_OPERATE_IMPORTER_READERBACKOFF":                 "1000",
		"CAMUNDA_REST_QUERY_ENABLED":                             "true",
		"CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED":                 "false",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY":   "1",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE":    "1",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX": "zeebe-record",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL":          "http://localhost:9200",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME":         "io.camunda.zeebe.exporter.ElasticsearchExporter",
		"ES_JAVA_HOME": javaHome,
		"ES_JAVA_OPTS": "-Xms1g -Xmx1g",
	}

	for key, value := range envVars {
		currentValue := os.Getenv(key)
		if currentValue != "" {
			continue
		}
		if err := os.Setenv(key, value); err != nil {
			return fmt.Errorf("failed to set environment variable %s: %w", key, err)
		}
	}

	return nil
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

func getBaseCommandSettings(baseCommand string) (C8RunSettings, error) {
	var settings C8RunSettings

	startFlagSet := createStartFlagSet(&settings)

	switch baseCommand {
	case "start":
		err := startFlagSet.Parse(os.Args[2:])
		if err != nil {
			return settings, fmt.Errorf("error parsing start argument: %w", err)
		}
	}

	return settings, nil
}

func createStartFlagSet(settings *C8RunSettings) *flag.FlagSet {
	startFlagSet := flag.NewFlagSet("start", flag.ExitOnError)
	startFlagSet.StringVar(&settings.config, "config", "", "Applies the specified configuration file.")
	startFlagSet.BoolVar(&settings.detached, "detached", false, "Starts Camunda Run as a detached process")
	return startFlagSet
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

	javaHome := os.Getenv("JAVA_HOME")
	javaBinary := "java"
	javaHomeAfterSymlink, err := filepath.EvalSymlinks(javaHome)
	if err != nil {
		fmt.Println("Failed to check if filepath is a symlink")
		os.Exit(1)
	}
	javaHome = javaHomeAfterSymlink
	if javaHome != "" {
		filepath.Walk(javaHome, func(path string, info os.FileInfo, err error) error {
			_, filename := filepath.Split(path)
			if strings.Compare(filename, "java.exe") == 0 || strings.Compare(filename, "java") == 0 {
				javaBinary = path
				return filepath.SkipAll
			}
			return nil
		})
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

	err = setEnvVars(javaHome)
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
		err := packages.New(camundaVersion, elasticsearchVersion, connectorsVersion)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	case "clean":
		cleanCommand(camundaVersion, elasticsearchVersion)
	}
}

func startCommand(c8 C8Run, settings C8RunSettings, processInfo processes, parentDir, javaBinary string, expectedJavaVersion int) {
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
	os.Setenv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")

	fmt.Print("Starting Elasticsearch " + processInfo.elasticsearch.version + "...\n")
	fmt.Print("(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)\n")

	elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
	elasticsearchLogFile, err := os.OpenFile(elasticsearchLogFilePath, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		fmt.Print("Failed to open file: " + elasticsearchLogFilePath)
		os.Exit(1)
	}

	elasticsearchCmd := c8.ElasticsearchCmd(processInfo.elasticsearch.version, parentDir)
	elasticsearchCmd.Stdout = elasticsearchLogFile
	elasticsearchCmd.Stderr = elasticsearchLogFile
	err = elasticsearchCmd.Start()
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}
	fmt.Print("Process id ", elasticsearchCmd.Process.Pid, "\n")

	elasticsearchPidFile, err := os.OpenFile(processInfo.elasticsearch.pid, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		fmt.Print("Failed to open file: " + processInfo.elasticsearch.pid)
		os.Exit(1)
	}
	elasticsearchPidFile.Write([]byte(strconv.Itoa(elasticsearchCmd.Process.Pid)))
	health.QueryElasticsearch("Elasticsearch", "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s")

	connectorsCmd := c8.ConnectorsCmd(javaBinary, parentDir, processInfo.camunda.version)
	connectorsLogPath := filepath.Join(parentDir, "log", "connectors.log")
	startApplication(connectorsCmd, processInfo.connectors.pid, connectorsLogPath)

	var extraArgs string
	if settings.config != "" {
		path := filepath.Join(parentDir, settings.config)
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
			extraArgs = "--spring.config.additional-location=file:" + settings.config + slash
		} else {
			extraArgs = "--spring.config.additional-location=file:" + settings.config
		}
	}

	camundaCmd := c8.CamundaCmd(processInfo.camunda.version, parentDir, extraArgs)
	camundaLogPath := filepath.Join(parentDir, "log", "camunda.log")
	startApplication(camundaCmd, processInfo.camunda.pid, camundaLogPath)
	err = health.QueryCamunda(c8, "Camunda", "http://localhost:8080/operate/login")
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

}

func stopCommand(c8 C8Run, settings C8RunSettings, processes processes) {
	err := stopProcess(c8, processes.elasticsearch.pid)
	if err != nil {
		fmt.Printf("%+v", err)
	}
	fmt.Println("Elasticsearch is stopped.")
	err = stopProcess(c8, processes.connectors.pid)
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
