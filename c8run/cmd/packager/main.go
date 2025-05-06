package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"

	"github.com/camunda/camunda/c8run/internal/packages"
	"github.com/joho/godotenv"
)

func usage(exitcode int) {
	fmt.Printf("Usage: %s [command] [options]\nCommands:\n package\n clean\n", os.Args[0])
	os.Exit(exitcode)
}

func getBaseCommand() (string, error) {
	if len(os.Args) == 1 {
		usage(1)
	}

	switch os.Args[1] {
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

func GetJavaVersion(javaBinary string) (string, error) {
	javaVersionCmd := exec.Command(javaBinary, "JavaVersion")
	var out strings.Builder
	var stderr strings.Builder
	javaVersionCmd.Stdout = &out
	javaVersionCmd.Stderr = &stderr
	err := javaVersionCmd.Run()
	if err != nil {
		return "", fmt.Errorf("failed to run java version command: %w", err)
	}
	javaVersionOutput := out.String()
	return javaVersionOutput, nil
}

func GetJavaHome(javaBinary string) (string, error) {
	javaHomeCmd := exec.Command(javaBinary, "JavaHome")
	var out strings.Builder
	var stderr strings.Builder
	javaHomeCmd.Stdout = &out
	javaHomeCmd.Stderr = &stderr
	err := javaHomeCmd.Run()
	if err != nil {
		return "", fmt.Errorf("failed to run java version command: %w", err)
	}
	javaHomeOutput := out.String()
	return javaHomeOutput, nil
}

func main() {
	err := godotenv.Load()
	if err != nil {
		fmt.Println(err.Error())
	}

	elasticsearchVersion := os.Getenv("ELASTICSEARCH_VERSION")
	camundaVersion := os.Getenv("CAMUNDA_VERSION")
	connectorsVersion := os.Getenv("CONNECTORS_VERSION")
	composeTag := os.Getenv("COMPOSE_TAG")

	baseCommand, err := getBaseCommand()
	if err != nil {
		fmt.Println(err.Error())
	}

	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}

	switch baseCommand {
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

type processes struct {
	camunda       process
	connectors    process
	elasticsearch process
}

type process struct {
	version string
	pid     string
}

func cleanCommand(camundaVersion string, elasticsearchVersion string) {
	packages.Clean(camundaVersion, elasticsearchVersion)
}
