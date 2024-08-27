package main

import (
	"os"
	"os/exec"
	"filepath"
	"fmt"
	"strings"
)


type c8runSettings struct {
	config string
	detached bool
}


func printHelp() {
	optionsHelp := `Options:
   --config     - Applies the specified configuration file.
   --detached   - Starts Camunda Run as a detached process
`
	fmt.Print(optionsHelp)

}


func parseCommandLineOptions(args []string, settings *c8runSettings) *c8runSettings {
	if len(args) == 0 {
		return settings
	}
	argsToPop := 0
	switch args[0] {
		case "--config":
			if len(args) > 1 {
				argsToPop = 1
				settings.config = args[1]
				printHelp()
				os.Exit(1)
			}
		case "--detached": settings.detached = true
		default:
			argsToPop = 2
			printHelp()
			os.Exit(1)

	}
	return parseCommandLineOptions(args[argsToPop - 1:], settings)

}


func main() {
	baseDir, _ := os.Getwd()
	// parentDir, _ := filepath.Dir(baseDir)
	parentDir, _ := baseDir
	deploymentDir := filepath.Join(parentDir, "configuration", "resources")
	elasticsearchVersion := "8.13.4"

	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME", "io.camunda.zeebe.exporter.ElasticsearchExporter")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://localhost:9200")
	os.Setenv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", "zeebe-record")

	detachProcess := false
	classPath := filepath.Join(parentDir, "configuration", "userlib") + "," + filepath.Join(parentDir, "configuration", "keystore")

	baseCommand := ""
	insideConfigFlag := false

	if os.Args[1] == "start" {
		baseCommand = "start"
	} else {
		baseCommand = "stop"
	}


	javaBinary := os.Getenv("JAVA")
	javaHome := os.Getenv("JAVA_HOME")

	if javaBinary == "" {
		fmt.Print("JAVA_HOME is not set. Unexpected results may occur.\n")
		fmt.Print("Set JAVA_HOME to the directory of your local JDK to avoid this message.\n")
		os.Setenv("JAVA", "java")
		javaBinary = "java"
	} else {
		fmt.Print("Setting JAVA property to " + javaHome + "\\bin\\java")
		javaBinary = filepath.Join(javaHome, "bin", "java")
		os.Setenv("JAVA", javaBinary)
	}

	javaVersion := os.Getenv("JAVA_VERSION")
	if javaVersion == "" {
		javaVersionCmd := exec.Command(javaBinary + " --version")
		var out strings.Builder
		javaVersionCmd.Stdout = &out
		javaVersionCmd.Run()
		output := strings.Split(out.String())[1]
		os.Setenv("JAVA_VERSION", output)
		javaVersion = output
	}

	fmt.Print("Java version is " + javaVersion + "\n")
	javaMajorVersion := strings.Split(javaVersion, ".")[0]
	javaMajorVersionInt := strconv.Atoi(javaVersion)
	expectedJavaVersion := 21
	if javaMajorVersionInt <= expectedJavaVersion {
		fmt.Print("You must use at least JDK " + strconv.itoA(expectedJavaVersion) + " to start Camunda Platform Run.\n")
		os.Exit(1)

	}

	javaOpts := os.Getenv("JAVA_OPTS")
	if javaOpts != "" {
		fmt.Print("JAVA_OPTS: " + javaOpts + "\n")
	}

	os.Setenv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")

	fmt.Print("Starting Elasticsearch " + elasticsearchVersion + "...")
	fmt.Print("(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)\n")

	elasticsearchLogFilePath := filepath.Join(parentDir, "log", "elasticsearch.log")
	elasticsearchLogFile, err := os.OpenFile(elasticsearchLogFilePath, os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		fmt.Print("Failed to open file: " + elasticsearchLogFilePath)
		os.Exit(1)
	}


	elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-" + elasticsearchVersion, "bin", "elasticsearch.bat") + "-E xpack.ml.enabled=false -E xpack.security.enabled=false"
	elasticsearchCmd := exec.Command(elasticsearchCmdString)
	elasticsearchCmd.Stdout = elasticsearchLogFile
	elasticsearchCmd.Stderr = elasticsearchLogFile
	javaVersionCmd.Run()


}
