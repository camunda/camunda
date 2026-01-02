package values

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"

	"camunda.com/helmunusedvalues/pkg/output"
	"camunda.com/helmunusedvalues/pkg/utils"
	"github.com/schollz/progressbar/v3"
)

type Extractor struct {
	Display *output.Display
}

func NewExtractor(display *output.Display) *Extractor {
	return &Extractor{
		Display: display,
	}
}

func (e *Extractor) FilterKeys(keys []string, pattern string) []string {
	if pattern == "" {
		return keys
	}

	var filtered []string
	for _, key := range keys {
		if strings.Contains(key, pattern) {
			filtered = append(filtered, key)
		}
	}

	e.Display.DebugLog(fmt.Sprintf("Filtered keys: %d/%d match pattern '%s'\n",
		len(filtered), len(keys), pattern))

	return filtered
}

func (e *Extractor) ExtractKeys(valuesFile string) ([]string, error) {
	return e.ExtractKeysWithProgress(valuesFile, nil)
}

func (e *Extractor) ExtractKeysWithProgress(valuesFile string, bar *progressbar.ProgressBar) ([]string, error) {
	if err := utils.ValidateFile(valuesFile); err != nil {
		return nil, fmt.Errorf("values file %s not found: %w", valuesFile, err)
	}

	if bar != nil {
		bar.Describe("Running yq to parse YAML...")
	}

	// Use yq to convert YAML to JSON
	yqCmd := exec.Command("yq", "eval", valuesFile, "-o", "json")
	yqOutput, err := yqCmd.Output()
	if err != nil {
		return nil, fmt.Errorf("failed to run yq: %w", err)
	}

	if bar != nil {
		bar.Describe("Extracting paths with jq...")
	}

	// Use jq to extract paths. This command will convert the JSON output from yq into a flat list of keys.
	jqCmd := exec.Command("jq", "[paths(scalars) as $p | {($p | join(\".\")): getpath($p)}] | add | keys[]")
	jqCmd.Stdin = strings.NewReader(string(yqOutput))
	jqOutput, err := jqCmd.Output()
	if err != nil {
		return nil, fmt.Errorf("failed to parse JSON with jq: %w", err)
	}

	if bar != nil {
		bar.Describe("Processing key list...")
	}

	var keys []string
	scanner := bufio.NewScanner(strings.NewReader(string(jqOutput)))
	for scanner.Scan() {
		key := scanner.Text()
		// Remove quotes from the key
		key = strings.Trim(key, "\"")
		if key != "" {
			keys = append(keys, key)
		}
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("error reading jq output: %w", err)
	}

	if len(keys) == 0 {
		return nil, fmt.Errorf("no keys extracted from values.yaml")
	}

	if bar != nil {
		bar.Describe(fmt.Sprintf("Found %d keys", len(keys)))
		bar.Finish()
	}

	e.Display.DebugLog(fmt.Sprintf("Extracted %d keys from values.yaml\n", len(keys)))

	return keys, nil
}
