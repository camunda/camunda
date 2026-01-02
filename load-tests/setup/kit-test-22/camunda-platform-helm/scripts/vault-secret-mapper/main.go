package main

import (
	"flag"
	"fmt"
	"os"
	"scripts/camunda-core/pkg/logging"
	"vault-secret-mapper/pkg/mapper"
)

func main() {
	// Initialize logger with default options
	_ = logging.Setup(logging.Options{
		LevelString:  "info",
		ColorEnabled: logging.IsTerminal(os.Stdout.Fd()),
	})

	mapping := flag.String("mapping", "", "Vault secret mapping content (multi-line, semicolon-terminated entries)")
	secretName := flag.String("secret-name", "vault-mapped-secrets", "Kubernetes Secret name to generate")
	outputPath := flag.String("output", "", "Path to write the generated Secret YAML")
	flag.Parse()

	if *outputPath == "" {
		exitWithError("missing required flag: --output")
	}
	if *mapping == "" {
		exitWithError("missing required flag: --mapping")
	}

	if err := mapper.Generate(*mapping, *secretName, *outputPath); err != nil {
		exitWithError("%v", err)
	}
}

func exitWithError(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	fmt.Fprintf(os.Stderr, "vault-secret-mapper error: %s\n", msg)
	os.Exit(1)
}
