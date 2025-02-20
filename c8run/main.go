package main

import (
	"crypto/tls"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"text/template"
	"time"

	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"github.com/joho/godotenv"
)

func printStatus(port int) error {
	endpoints, _ := os.ReadFile("endpoints.txt")
	t, err := template.New("endpoints").Parse(string(endpoints))
	if err != nil {
		return fmt.Errorf("Error: failed to parse endpoints template: %s", err.Error())
	}

	data := TemplateData{
		ServerPort: port,
	}

	err = t.Execute(os.Stdout, data)
	if err != nil {
		return fmt.Errorf("Error: failed to fill endpoints template %s", err.Error())
	}
	return nil
}

func queryElasticsearchHealth(name string, url string) {
	if isRunning(name, url, 12, 10*time.Second) {
		fmt.Println(name + " has successfully been started.")
	} else {
		fmt.Println("Error: " + name + " did not start!")
		os.Exit(1)
	}
}

func queryCamundaHealth(c8 C8Run, name string, settings C8RunSettings) error {
	protocol := "http"
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	if settings.keystore != "" && settings.keystorePassword != "" {
		protocol = "https"
	}
	url := protocol + "://localhost:9600/actuator/health"
	if isRunning(name, url, 24, 14*time.Second) {
		fmt.Println(name + " has successfully been started.")
		err := c8.OpenBrowser(protocol, settings.port)
		if err != nil {
			fmt.Println("Failed to open browser")
			return nil
		}
		if err := printStatus(settings.port); err != nil {
			fmt.Println("Failed to print status:", err)
			return err
		}
		return nil
	} else {
		return fmt.Errorf("Error: %s did not start!", name)
	}
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

func stopProcess(c8 C8Run, pidfile string) error {
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

func getC8RunPlatform() C8Run {
	if runtime.GOOS == "windows" {
		return &windows.WindowsC8Run{}
	} else if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
		return &unix.UnixC8Run{}
	}
	panic("Unsupported operating system")
}

func adjustJavaOpts(javaOpts string, settings C8RunSettings) string {
	protocol := "http"
	if settings.keystore != "" && settings.keystorePassword != "" {
		javaOpts = javaOpts + " -Dserver.ssl.keystore=file:" + settings.keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.keystorePassword
		protocol = "https"
	}
	if settings.port != 8080 {
		javaOpts = javaOpts + " -Dserver.port=" + strconv.Itoa(settings.port)
	}
	if settings.username != "" || settings.password != "" {
		javaOpts = javaOpts + " -Dzeebe.broker.exporters.camundaExporter.args.createSchema=true"
		javaOpts = javaOpts + " -Dzeebe.broker.exporters.camundaExporter.className=io.camunda.exporter.CamundaExporter"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].name=Demo"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].email=demo@demo.com"
	}
	if settings.username != "" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].username=" + settings.username
	}
	if settings.password != "" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].password=" + settings.password
	}
	javaOpts = javaOpts + " -Dspring.profiles.active=operate,tasklist,broker,identity,consolidated-auth"
	os.Setenv("CAMUNDA_OPERATE_ZEEBE_RESTADDRESS", protocol+"://localhost:"+strconv.Itoa(settings.port))
	fmt.Println("Java opts: " + javaOpts)
	return javaOpts
}

func validateKeystore(settings C8RunSettings, parentDir string) error {
	if settings.keystore == "" {
		return nil
	}

	if settings.keystorePassword == "" {
		return fmt.Errorf("you must provide a password with --keystorePassword to unlock your keystore")
	}

	if !strings.HasPrefix(settings.keystore, "/") {
		settings.keystore = filepath.Join(parentDir, settings.keystore)
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

func setEnvVars() error {
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
	}

	for key, value := range envVars {
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

func handleDockerCommand(settings C8RunSettings, baseCommand string, composeExtractedFolder string) error {
	if !settings.docker {
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

func getBaseCommandSettings(baseCommand string) (C8RunSettings, error) {
	var settings C8RunSettings

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

func createStartFlagSet(settings *C8RunSettings) *flag.FlagSet {
	startFlagSet := flag.NewFlagSet("start", flag.ExitOnError)
	startFlagSet.StringVar(&settings.config, "config", "", "Applies the specified configuration file.")
	startFlagSet.BoolVar(&settings.detached, "detached", false, "Starts Camunda Run as a detached process")
	startFlagSet.IntVar(&settings.port, "port", 8080, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.keystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")
	startFlagSet.StringVar(&settings.logLevel, "log-level", "", "Adjust the log level of Camunda")
	startFlagSet.BoolVar(&settings.disableElasticsearch, "disable-elasticsearch", false, "Do not start or stop Elasticsearch (still requires Elasticsearch to be running outside of c8run)")
	startFlagSet.BoolVar(&settings.docker, "docker", false, "Run Camunda from docker-compose.")
	startFlagSet.StringVar(&settings.username, "username", "demo", "Change the first users username (default: demo)")
	startFlagSet.StringVar(&settings.password, "password", "demo", "Change the first users password (default: demo)")
	return startFlagSet
}

func createStopFlagSet(settings *C8RunSettings) *flag.FlagSet {
	stopFlagSet := flag.NewFlagSet("stop", flag.ExitOnError)
	stopFlagSet.BoolVar(&settings.disableElasticsearch, "disable-elasticsearch", false, "Do not stop Elasticsearch")
	stopFlagSet.BoolVar(&settings.docker, "docker", false, "Stop docker-compose distribution of camunda.")
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

	elasticsearchPidPath := filepath.Join(baseDir, "elasticsearch.pid")
	connectorsPidPath := filepath.Join(baseDir, "connectors.pid")
	camundaPidPath := filepath.Join(baseDir, "camunda.pid")

	err = setEnvVars()
	if err != nil {
		fmt.Println("Failed to set envVars:", err)
	}

	// classPath := filepath.Join(parentDir, "configuration", "userlib") + "," + filepath.Join(parentDir, "configuration", "keystore")

	baseCommand, err := getBaseCommand()
	if err != nil {
		fmt.Println(err.Error())
	}

	settings, err := getBaseCommandSettings(baseCommand)
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	if settings.logLevel != "" {
		os.Setenv("ZEEBE_LOG_LEVEL", settings.logLevel)
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
	os.Setenv("ES_JAVA_HOME", javaHome)

	if baseCommand == "start" {
		javaVersion := os.Getenv("JAVA_VERSION")
		if javaVersion == "" {
			javaVersionCmd := c8.VersionCmd(javaBinary)
			var out strings.Builder
			var stderr strings.Builder
			javaVersionCmd.Stdout = &out
			javaVersionCmd.Stderr = &stderr
			err = javaVersionCmd.Run()
			if err != nil {
				fmt.Println(err.Error())
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
		javaOpts = adjustJavaOpts(javaOpts, settings)

		os.Setenv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")

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
			_, err = elasticsearchPidFile.Write([]byte(strconv.Itoa(elasticsearchCmd.Process.Pid)))
			if err != nil {
				fmt.Print("Failed to write to file: " + elasticsearchPidPath + " continuing...")
			}
			queryElasticsearchHealth("Elasticsearch", "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s")
		}

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
		camundaCmd := c8.CamundaCmd(camundaVersion, parentDir, "", javaOpts)
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
		_, err = camundaPidFile.Write([]byte(strconv.Itoa(camundaCmd.Process.Pid)))
		if err != nil {
			fmt.Print("Failed to write to file: " + camundaPidPath + " continuing...")
		}

		err = queryCamundaHealth(c8, "Camunda", settings)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	}

	if baseCommand == "stop" {
		if !settings.disableElasticsearch {
			err = stopProcess(c8, elasticsearchPidPath)
			if err != nil {
				fmt.Printf("%+v", err)
			}
			fmt.Println("Elasticsearch is stopped.")
		}
		err = stopProcess(c8, connectorsPidPath)
		if err != nil {
			fmt.Printf("%+v", err)
		}
		fmt.Println("Connectors is stopped.")
		err = stopProcess(c8, camundaPidPath)
		if err != nil {
			fmt.Printf("%+v", err)
		}
		fmt.Println("Camunda is stopped.")
	}

	if baseCommand == "package" {
		err := Package(camundaVersion, elasticsearchVersion, connectorsVersion, composeTag)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	}

	if baseCommand == "clean" {
		Clean(camundaVersion, elasticsearchVersion)
	}
}
