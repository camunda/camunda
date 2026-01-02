package mapper

import (
	"fmt"
	"os"
	"scripts/camunda-core/pkg/logging"
	"strings"

	"gopkg.in/yaml.v3"
)

// Secret represents a Kubernetes Secret
type Secret struct {
	APIVersion string            `yaml:"apiVersion"`
	Kind       string            `yaml:"kind"`
	Metadata   Metadata          `yaml:"metadata"`
	Type       string            `yaml:"type"`
	StringData map[string]string `yaml:"stringData"`
}

type Metadata struct {
	Name   string            `yaml:"name"`
	Labels map[string]string `yaml:"labels"`
}

func Generate(mapping, secretName, outputPath string) error {
	logging.Logger.Debug().
		Str("secretName", secretName).
		Str("outputPath", outputPath).
		Msg("Generating secret from mapping")

	// Parse the mapping and derive env var names to include in the Secret
	logging.Logger.Debug().Str("mapping", mapping).Msg("Parsing mapping")
	envVarNames := parseMapping(mapping)
	envVarNames = dedupePreserveOrder(envVarNames)

	logging.Logger.Debug().Int("count", len(envVarNames)).Msg("Found env vars to map")
    logging.Logger.Debug().Strs("envVarNames", envVarNames).Msg("Env var names")
	// Collect non-empty env vars from environment
	stringData := make(map[string]string)
	for _, name := range envVarNames {
		if name == "" {
			continue
		}
		val := os.Getenv(name)
		if val == "" {
			// Skip empty values to avoid creating empty keys
			logging.Logger.Debug().Str("var", name).Msg("Environment variable empty or missing, skipping")
			continue
		}
		stringData[name] = val
	}
	logging.Logger.Info().Int("mappedCount", len(stringData)).Msg("Mapped environment variables to secret")

	// Build Labels
	labels := map[string]string{
		"managed-by": "test-integration-runner",
	}
	if jobID := os.Getenv("GITHUB_WORKFLOW_JOB_ID"); jobID != "" {
		labels["github-id"] = jobID
	}

	secret := Secret{
		APIVersion: "v1",
		Kind:       "Secret",
		Metadata: Metadata{
			Name:   secretName,
			Labels: labels,
		},
		Type:       "Opaque",
		StringData: stringData,
	}

	// Marshal to YAML
	yamlBytes, err := yaml.Marshal(&secret)
	if err != nil {
		return fmt.Errorf("marshal YAML: %v", err)
	}

	// Write to file
	if err := os.WriteFile(outputPath, yamlBytes, 0o600); err != nil {
		return fmt.Errorf("write output: %v", err)
	}

	return nil
}

// parseMapping extracts the env var names from the provided mapping.
// Input format examples (one per line; trailing ';' optional):
//
//	path/to/secret KEY1 | ALIAS1;
//	path/to/secret KEY1,KEY2 | ALIAS1,ALIAS2;
//	path/to/secret KEY1;
//
// When alias list is present after '|', we use aliases; otherwise use key names.
func parseMapping(mapping string) []string {
	// Normalize separators: treat newlines as semicolons, then split by semicolon.
	// This handles both "one entry per line" and "semicolon-separated entries" (even on same line).
	mapping = strings.ReplaceAll(mapping, "\n", ";")
	lines := strings.Split(mapping, ";")

	var names []string
	for _, raw := range lines {
		line := trimSpaceAndCR(raw)
		if line == "" {
			continue
		}
		// (Semicolons already removed by Split)

		// Skip comments
		if strings.HasPrefix(strings.TrimSpace(line), "#") {
			continue
		}
		// Split first space: path and remainder
		parts := splitOnce(line, " ")
		if len(parts) != 2 {
			// malformed; skip
			continue
		}
		rest := strings.TrimSpace(parts[1])
		if rest == "" {
			continue
		}

		if idx := strings.Index(rest, "|"); idx >= 0 {
			aliasPart := strings.TrimSpace(rest[idx+1:])
			if aliasPart == "" {
				logging.Logger.Debug().Str("line", line).Msg("Empty alias part in mapping line")
				continue
			}
			for _, n := range splitCSVOrSpace(aliasPart) {
				n = strings.TrimSpace(n)
				if n != "" {
					names = append(names, n)
				}
			}
		} else {
			// Use original keys (before alias)
			keysPart := strings.TrimSpace(rest)
			for _, n := range splitCSVOrSpace(keysPart) {
				n = strings.TrimSpace(n)
				if n != "" {
					names = append(names, n)
				}
			}
		}
	}
	return names
}

func trimSpaceAndCR(s string) string {
	s = strings.TrimRight(s, "\r")
	return strings.TrimSpace(s)
}

func splitOnce(s, sep string) []string {
	i := strings.Index(s, sep)
	if i < 0 {
		return []string{s}
	}
	return []string{s[:i], s[i+len(sep):]}
}

func splitCSVOrSpace(s string) []string {
	// Replace commas with spaces, then split on spaces
	s = strings.ReplaceAll(s, ",", " ")
	fields := strings.Fields(s)
	return fields
}

func dedupePreserveOrder(in []string) []string {
	seen := make(map[string]struct{}, len(in))
	out := make([]string, 0, len(in))
	for _, v := range in {
		if v == "" {
			continue
		}
		if _, ok := seen[v]; ok {
			continue
		}
		seen[v] = struct{}{}
		out = append(out, v)
	}
	return out
}

