package scenarios

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

const (
	ValuesFilePrefix = "values-integration-test-ingress-"
)

// ResolvePath determines the source values file path for a scenario.
func ResolvePath(scenariosDir, scenario string) (string, error) {
	// Handle case where user provides full filename or just suffix
	var filename string
	if strings.HasPrefix(scenario, ValuesFilePrefix) && strings.HasSuffix(scenario, ".yaml") {
		filename = scenario
	} else {
		filename = fmt.Sprintf("%s%s.yaml", ValuesFilePrefix, scenario)
	}

	sourceValuesFile := filepath.Join(scenariosDir, filename)
	if _, err := os.Stat(sourceValuesFile); err != nil {
		return "", fmt.Errorf("scenario values file not found: %w", err)
	}
	return sourceValuesFile, nil
}

// List returns a list of available scenario names (stripped of prefix/suffix)
func List(scenariosDir string) ([]string, error) {
	entries, err := os.ReadDir(scenariosDir)
	if err != nil {
		return nil, err
	}

	var scenarios []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasPrefix(e.Name(), ValuesFilePrefix) && strings.HasSuffix(e.Name(), ".yaml") {
			name := strings.TrimPrefix(e.Name(), ValuesFilePrefix)
			name = strings.TrimSuffix(name, ".yaml")
			scenarios = append(scenarios, name)
		}
	}
	return scenarios, nil
}
