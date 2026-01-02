package deployer

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type ScenarioMeta struct {
	Name string
	Path string
	Desc string
}

func ResolveScenarioFiles(scenarioDir string, scenarios []string) ([]string, error) {
	if len(scenarios) == 0 {
		return nil, nil
	}
	var files []string
	var missing []string
	for _, s := range scenarios {
		s = strings.TrimSpace(s)
		if s == "" {
			continue
		}
		p := filepath.Join(scenarioDir, fmt.Sprintf("values-integration-test-ingress-%s.yaml", s))
		if _, err := os.Stat(p); err != nil {
			missing = append(missing, s)
			continue
		}
		files = append(files, p)
	}
	if len(missing) > 0 {
		var errMsgs []string
		for _, miss := range missing {
			filePath := filepath.Join(scenarioDir, fmt.Sprintf("values-integration-test-ingress-%s.yaml", miss))
			errMsgs = append(errMsgs, fmt.Sprintf("missing scenario values file: %s", filePath))
		}
		return nil, fmt.Errorf("%s", strings.Join(errMsgs, "; "))
	}
	return files, nil
}

