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

	camundaVersion := os.Getenv("CAMUNDA_VERSION")
	connectorsVersion := os.Getenv("CONNECTORS_VERSION")

	// Sync .env with resolved versions so the bundled package is self-consistent.
	// CAMUNDA_VERSION may be overridden via the environment (e.g. 8.10.0-SNAPSHOT on a branch)
	// while .env still has a stale released version.
	envContent := "# this is the version of camunda/ zeebe\nCAMUNDA_VERSION=" + camundaVersion + "\n# Look here: https://artifacts.camunda.com/ui/native/connectors/io/camunda/connector/connector-runtime-bundle/\nCONNECTORS_VERSION=" + connectorsVersion + "\n"
	if err := os.WriteFile(".env", []byte(envContent), 0644); err != nil {
		fmt.Printf("warning: could not update .env: %v\n", err)
	}

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
		err := packages.New(camundaVersion, connectorsVersion)
		if err != nil {
			fmt.Printf("%+v", err)
			os.Exit(1)
		}
	case "clean":
		cleanCommand(camundaVersion)
	}
}

func cleanCommand(camundaVersion string) {
	packages.Clean(camundaVersion)
}
