package main

import (
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
)

type c8runSettings struct {
	config   string
	detached bool
}

func printHelp() {
	optionsHelp := `Options:
   --config     - Applies the specified configuration file.
   --detached   - Starts Camunda Run as a detached process
`
	fmt.Print(optionsHelp)

}

func printStatus() {
	endpoints, _ := os.ReadFile(".\\endpoints.txt")
	fmt.Println(string(endpoints))
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

func queryCamundaHealth(name string, url string) {
	healthy := false
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
		fmt.Println("Error: " + name + " did not start!")
		os.Exit(1)
	}
	operateUrl := "http://localhost:8080/operate/login"
	openBrowserCmdString := "start " + operateUrl
	openBrowserCmd := exec.Command("cmd", "/C", openBrowserCmdString)
	openBrowserCmd.SysProcAttr = &syscall.SysProcAttr{
		// CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	fmt.Println(name + " has successfully been started.")
	openBrowserCmd.Run()
	printStatus()
}

func stopProcess(pidfile string) {
	if _, err := os.Stat(pidfile); err == nil {
		commandPidText, _ := os.ReadFile(pidfile)
		commandPidStripped := strings.TrimSpace(string(commandPidText))
		commandPid, _ := strconv.Atoi(string(commandPidStripped))

		for _, process := range process_tree(int(commandPid)) {
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

func parseCommandLineOptions(args []string, settings *c8runSettings) *c8runSettings {
	if len(args) == 0 {
		return settings
	}
	argsToPop := 0
	switch args[0] {
	case "--config":
		if len(args) > 1 {
			argsToPop = 2
			settings.config = args[1]
		} else {
			printHelp()
			os.Exit(1)
		}
	case "--detached":
		settings.detached = true
	default:
		argsToPop = 2
		printHelp()
		os.Exit(1)

	}
	return parseCommandLineOptions(args[argsToPop:], settings)

}

func main() {
	baseDir, _ := os.Getwd()
	// parentDir, _ := filepath.Dir(baseDir)
	parentDir := baseDir
	// deploymentDir := filepath.Join(parentDir, "configuration", "resources")
	elasticsearchVersion := "8.13.4"
	camundaVersion := "8.6.0"
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

	// classPath := filepath.Join(parentDir, "configuration", "userlib") + "," + filepath.Join(parentDir, "configuration", "keystore")

	baseCommand := ""
	// insideConfigFlag := false

	if os.Args[1] == "start" {
		baseCommand = "start"
	} else {
		baseCommand = "stop"
	}
	fmt.Print("Command: " + baseCommand + "\n")

	var settings c8runSettings
	if len(os.Args) > 2 {
		parseCommandLineOptions(os.Args[2:], &settings)
	}

        javaHome := os.Getenv("JAVA_HOME")
	javaBinary := "java"
        if javaHome != "" {
	        javaBinary = filepath.Join(javaHome, "bin", "java")
        }

	if baseCommand == "start" {
		javaVersion := os.Getenv("JAVA_VERSION")
		if javaVersion == "" {
			javaVersionCmd := exec.Command(javaBinary + " --version")
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

		os.Setenv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")

		fmt.Print("Starting Elasticsearch " + elasticsearchVersion + "...\n")
		fmt.Print("(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)\n")

		elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
		elasticsearchLogFile, err := os.OpenFile(elasticsearchLogFilePath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + elasticsearchLogFilePath)
			os.Exit(1)
		}

		elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch.bat") + " -E xpack.ml.enabled=false -E xpack.security.enabled=false"
		elasticsearchCmd := exec.Command("cmd", "/C", elasticsearchCmdString)

		elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{
			CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		}
		elasticsearchCmd.Stdout = elasticsearchLogFile
		elasticsearchCmd.Stderr = elasticsearchLogFile
		elasticsearchCmd.Start()
		fmt.Print("Process id ", elasticsearchCmd.Process.Pid, "\n")

		elasticsearchPidFile, err := os.OpenFile(elasticsearchPidPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + elasticsearchPidPath)
			os.Exit(1)
		}
		elasticsearchPidFile.Write([]byte(strconv.Itoa(elasticsearchCmd.Process.Pid)))
		queryElasticsearchHealth("Elasticsearch", "http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s")

		connectorsCmdString := javaBinary + " -classpath " + parentDir + "\\*;" + parentDir + "\\custom_connectors\\*;" + parentDir + "\\camunda-zeebe-" + camundaVersion + "\\lib\\* io.camunda.connector.runtime.app.ConnectorRuntimeApplication --spring.config.location=" + parentDir + "\\connectors-application.properties"
		connectorsCmd := exec.Command("cmd", "/C", connectorsCmdString)
		connectorsLogPath := filepath.Join(parentDir, "log", "connectors.log")
		connectorsLogFile, err := os.OpenFile(connectorsLogPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + connectorsLogPath)
			os.Exit(1)
		}
		connectorsCmd.SysProcAttr = &syscall.SysProcAttr{
			CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		}
		connectorsCmd.Stdout = connectorsLogFile
		connectorsCmd.Stderr = connectorsLogFile
		connectorsCmd.Start()

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
		camundaCmdString := parentDir + "\\camunda-zeebe-" + camundaVersion + "\\bin\\camunda " + extraArgs
		fmt.Println(camundaCmdString)
		camundaCmd := exec.Command("cmd", "/C", camundaCmdString)
		camundaLogPath := filepath.Join(parentDir, "log", "camunda.log")
		camundaLogFile, err := os.OpenFile(camundaLogPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + camundaLogPath)
			os.Exit(1)
		}
		camundaCmd.SysProcAttr = &syscall.SysProcAttr{
			CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
			// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		}
		camundaCmd.Stdout = camundaLogFile
		camundaCmd.Stderr = camundaLogFile
		camundaCmd.Start()
		camundaPidFile, err := os.OpenFile(camundaPidPath, os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			fmt.Print("Failed to open file: " + camundaPidPath)
			os.Exit(1)
		}
		camundaPidFile.Write([]byte(strconv.Itoa(camundaCmd.Process.Pid)))
		queryCamundaHealth("Camunda", "http://localhost:8080/operate/login")
	}

	if baseCommand == "stop" {
		stopProcess(elasticsearchPidPath)
		fmt.Println("Elasticsearch is stopped.")
		stopProcess(connectorsPidPath)
		fmt.Println("Connectors is stopped.")
		stopProcess(camundaPidPath)
		fmt.Println("Camunda is stopped.")
	}

}
