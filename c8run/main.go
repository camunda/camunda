package main

import (
	"crypto/tls"
	"errors"
	"flag"
	"fmt"
	"github.com/camunda/camunda/c8run/internal/unix"
	"github.com/camunda/camunda/c8run/internal/windows"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"text/template"
	"time"
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
	healthy := false
	for retries := 12; retries >= 0; retries-- {
		fmt.Println("Waiting for " + name + " to start. " + strconv.Itoa(retries) + " retries left")
		time.Sleep(10 * time.Second)
		resp, err := http.Get(url)
		if err != nil {
			continue
		}
		if resp.StatusCode >= 200 && resp.StatusCode <= 400 {
			healthy = true
			break
		}
	}
	if !healthy {
		fmt.Println("Error: " + name + " did not start!")
		os.Exit(1)
	}
	fmt.Println(name + " has successfully been started.")
}

func queryCamundaHealth(c8 C8Run, name string, settings C8RunSettings) error {
	healthy := false

	protocol := "http"
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	if settings.keystore != "" && settings.keystorePassword != "" {
		protocol = "https"
	}
	url := protocol + "://localhost:" + strconv.Itoa(settings.port) + "/operate/login"
	for retries := 24; retries >= 0; retries-- {
		fmt.Println("Waiting for " + name + " to start. " + strconv.Itoa(retries) + " retries left")
		time.Sleep(14 * time.Second)
		resp, err := http.Get(url)
		if err != nil {
			continue
		}
		if resp.StatusCode >= 200 && resp.StatusCode <= 400 {
			healthy = true
			break
		}
	}
	if !healthy {
		return fmt.Errorf("Error: %s did not start!", name)
	}
	fmt.Println(name + " has successfully been started.")
	err := c8.OpenBrowser(protocol, settings.port)
	if err != nil {
		// failing to open the browser is not a critical error. It could simply be a sign the script is running in a CI node without a browser installed, or a docker image.
		fmt.Println("Failed to open browser")
		return nil
	}
	printStatus(settings.port)
	return nil
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

func adjustJavaOpts(javaOpts string, settings C8RunSettings) string {
	protocol := "http"
	if settings.keystore != "" && settings.keystorePassword != "" {
		javaOpts = javaOpts + " -Dserver.ssl.keystore=file:" + settings.keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.keystorePassword
		protocol = "https"
	}
	if settings.port != 8080 {
		javaOpts = javaOpts + " -Dserver.port=" + strconv.Itoa(settings.port)
	}
	os.Setenv("CAMUNDA_OPERATE_ZEEBE_RESTADDRESS", protocol+"://localhost:"+strconv.Itoa(settings.port))
	return javaOpts
}

func validateKeystore(settings C8RunSettings, parentDir string) error {
	if settings.keystore != "" {
		if settings.keystorePassword == "" {
			return fmt.Errorf("You must provide a password with --keystorePassword to unlock your keystore.")
		}
		if settings.keystore != "" {
			if !strings.HasPrefix(settings.keystore, "/") {
				settings.keystore = filepath.Join(parentDir, settings.keystore)
			}
		}
	}
	return nil
}

func main() {
	c8 := getC8RunPlatform()
	baseDir, _ := os.Getwd()
	// parentDir, _ := filepath.Dir(baseDir)
	parentDir := baseDir
	// deploymentDir := filepath.Join(parentDir, "configuration", "resources")
	elasticsearchVersion := "8.13.4"
	camundaVersion := "8.7.0-alpha1"
	if os.Getenv("CAMUNDA_VERSION") != "" {
		camundaVersion = os.Getenv("CAMUNDA_VERSION")
	}
	expectedJavaVersion := 21

	elasticsearchPidPath := filepath.Join(baseDir, "elasticsearch.pid")
	connectorsPidPath := filepath.Join(baseDir, "connectors.pid")
	camundaPidPath := filepath.Join(baseDir, "camunda.pid")

	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME", "io.camunda.zeebe.exporter.ElasticsearchExporter")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://localhost:9200")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", "zeebe-record")

	os.Setenv("CAMUNDA_REST_QUERY_ENABLED", "true")
	os.Setenv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false")
	os.Setenv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false")
	os.Setenv("CAMUNDA_OPERATE_IMPORTER_READERBACKOFF", "1000")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")

	os.Setenv("CAMUNDA_OPERATE_IMPORTER_READERBACKOFF", "1000")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")

	// classPath := filepath.Join(parentDir, "configuration", "userlib") + "," + filepath.Join(parentDir, "configuration", "keystore")

	baseCommand := ""
	// insideConfigFlag := false

	if os.Args[1] == "start" {
		baseCommand = "start"
	} else if os.Args[1] == "stop" {
		baseCommand = "stop"
	} else if os.Args[1] == "package" {
		baseCommand = "package"
	} else if os.Args[1] == "clean" {
		baseCommand = "clean"
	} else if os.Args[1] == "-h" || os.Args[1] == "--help" {
		fmt.Println("Usage: c8run [command] [options]\nCommands:\n  start\n  stop\n  package")
		os.Exit(0)
	} else {
		panic("Unsupported operation")
	}
	fmt.Print("Command: " + baseCommand + "\n")

	var settings C8RunSettings
	startFlagSet := flag.NewFlagSet("start", flag.ExitOnError)
	startFlagSet.StringVar(&settings.config, "config", "", "Applies the specified configuration file.")
	startFlagSet.BoolVar(&settings.detached, "detached", false, "Starts Camunda Run as a detached process")
	startFlagSet.IntVar(&settings.port, "port", 8080, "Port to run Camunda on")
	startFlagSet.StringVar(&settings.keystore, "keystore", "", "Provide a JKS filepath to enable TLS")
	startFlagSet.StringVar(&settings.keystorePassword, "keystorePassword", "", "Provide a password to unlock your JKS keystore")

	switch baseCommand {
	case "start":
		startFlagSet.Parse(os.Args[2:])
	}

	err := validateKeystore(settings, parentDir)
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
	os.Setenv("ES_JAVA_HOME", javaHome)

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
		javaOpts = adjustJavaOpts(javaOpts, settings)

		os.Setenv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")

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
		connectorsPidFile.Write([]byte(strconv.Itoa(connectorsCmd.Process.Pid)))
		var extraArgs string
		if settings.config != "" {
			extraArgs = "--spring.config.location=" + filepath.Join(parentDir, settings.config)
		} else {
			extraArgs = "--spring.config.location=" + filepath.Join(parentDir, "configuration")
		}
		camundaCmd := c8.CamundaCmd(camundaVersion, parentDir, extraArgs, javaOpts)
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

		err = queryCamundaHealth(c8, "Camunda", settings)
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
		if runtime.GOOS == "windows" {
			err := PackageWindows(camundaVersion, elasticsearchVersion)
			if err != nil {
				fmt.Printf("%+v", err)
				os.Exit(1)
			}
		} else if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
			err := PackageUnix(camundaVersion, elasticsearchVersion)
			if err != nil {
				fmt.Printf("%+v", err)
				os.Exit(1)
			}
		} else {
			panic("Unsupported system")
		}
	}

	if baseCommand == "clean" {
		Clean(camundaVersion, elasticsearchVersion)
	}
}
