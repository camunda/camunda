package main

import (
	"crypto/tls"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
)

func getStatus() error {
	endpoints, err := os.ReadFile("endpoints.txt")
	fmt.Println(string(endpoints))
	return err
}

func queryElasticsearchHealth(name string, url string) {
	if isRunning(name, url, 12, 10*time.Second) {
		fmt.Println(name + " has successfully been started.")
	} else {
		fmt.Println("Error: " + name + " did not start!")
		os.Exit(1)
	}
}

func queryCamundaHealth(c8 C8Run, name string, url string) error {
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	if isRunning(name, url, 24, 14*time.Second) {
		fmt.Println(name + " has successfully been started.")
		err := c8.OpenBrowser()
		if err != nil {
			fmt.Println("Failed to open browser")
			return nil
		}
		if err := getStatus(); err != nil {
			fmt.Println("Failed to print status:", err)
			return err
		}
		return nil
	} else {
		return fmt.Errorf("Error: %s did not start!", name)
	}
	return nil
}

func isRunning(name, url string, retries int, delay time.Duration) bool {
	for retries >= 0 {
		fmt.Printf("Waiting for %s to start. %d retries left\n", name, retries)
		time.Sleep(delay)
		resp, err := http.Get(url)
		if err == nil && resp.StatusCode >= 200 && resp.StatusCode <= 400 {
			return true
		}
		retries--
	}
	return false
}

func stopProcess(c8 C8Run, pidfile string) {
	if _, err := os.Stat(pidfile); err == nil {
		commandPidText, _ := os.ReadFile(pidfile)
		commandPidStripped := strings.TrimSpace(string(commandPidText))
		commandPid, _ := strconv.Atoi(string(commandPidStripped))

		for _, process := range c8.ProcessTree(int(commandPid)) {
			process.Kill()
		}
		os.Remove(pidfile)

	} else if errors.Is(err, os.ErrNotExist) {
		// path/to/whatever does *not* exist

	} else {
		// Schrodinger: file may or may not exist. See err for details.

		// Therefore, do *NOT* use !os.IsNotExist(err) to test for file existence

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
	elasticsearchPidPath := filepath.Join(baseDir, "elasticsearch.pid")
	connectorsPidPath := filepath.Join(baseDir, "connectors.pid")
	camundaPidPath := filepath.Join(baseDir, "camunda.pid")

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

	if baseCommand == "start" {
		javaVersion := os.Getenv("JAVA_VERSION")
		if javaVersion == "" {
			javaVersionCmd := c8.VersionCmd(javaBinary)
			var out strings.Builder
			var stderr strings.Builder
			javaVersionCmd.Stdout = &out
			javaVersionCmd.Stderr = &stderr
			javaVersionCmd.Run()
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

		if !settings.disableElasticsearch {
			fmt.Print("Starting Elasticsearch " + elasticsearchVersion + "...\n")
			fmt.Print("(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)\n")

		elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
		elasticsearchLogFile, err := os.OpenFile(elasticsearchLogFilePath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + elasticsearchLogFilePath)
			os.Exit(1)
		}

		elasticsearchCmd := c8.ElasticsearchCmd(elasticsearchVersion, parentDir)
		elasticsearchCmd.Stdout = elasticsearchLogFile
		elasticsearchCmd.Stderr = elasticsearchLogFile
		err = elasticsearchCmd.Start()
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
		fmt.Print("Process id ", elasticsearchCmd.Process.Pid, "\n")

		elasticsearchPidFile, err := os.OpenFile(elasticsearchPidPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + elasticsearchPidPath)
			os.Exit(1)
		}
		elasticsearchPidFile.Write([]byte(strconv.Itoa(elasticsearchCmd.Process.Pid)))
		queryElasticsearchHealth("Elasticsearch", "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s")

		connectorsCmd := c8.ConnectorsCmd(javaBinary, parentDir, camundaVersion)
		connectorsLogPath := filepath.Join(parentDir, "log", "connectors.log")
		connectorsLogFile, err := os.OpenFile(connectorsLogPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + connectorsLogPath)
			os.Exit(1)
		}
		connectorsCmd.Stdout = connectorsLogFile
		connectorsCmd.Stderr = connectorsLogFile
		err = connectorsCmd.Start()
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}

		connectorsPidFile, err := os.OpenFile(connectorsPidPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + connectorsPidPath)
			os.Exit(1)
		}

		_, err = connectorsPidFile.Write([]byte(strconv.Itoa(connectorsCmd.Process.Pid)))
		if err != nil {
			fmt.Print("Failed to write to file: " + connectorsPidPath + " continuing...")
		}

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

		camundaCmd := c8.CamundaCmd(camundaVersion, parentDir, extraArgs)
		camundaLogPath := filepath.Join(parentDir, "log", "camunda.log")
		camundaLogFile, err := os.OpenFile(camundaLogPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + camundaLogPath)
			os.Exit(1)
		}
		camundaCmd.Stdout = camundaLogFile
		camundaCmd.Stderr = camundaLogFile
		err = camundaCmd.Start()
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
		camundaPidFile, err := os.OpenFile(camundaPidPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + camundaPidPath)
			os.Exit(1)
		}
		camundaPidFile.Write([]byte(strconv.Itoa(camundaCmd.Process.Pid)))
		err = queryCamundaHealth(c8, "Camunda", "http://localhost:8080/operate/login")
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	}

	if baseCommand == "stop" {
		stopProcess(c8, elasticsearchPidPath)
		fmt.Println("Elasticsearch is stopped.")
		stopProcess(c8, connectorsPidPath)
		fmt.Println("Connectors is stopped.")
		stopProcess(c8, camundaPidPath)
		fmt.Println("Camunda is stopped.")
	}

	if baseCommand == "package" {
		err := Package(camundaVersion, elasticsearchVersion, connectorsVersion)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	}

	if baseCommand == "clean" {
		Clean(camundaVersion, elasticsearchVersion)
	}
}
