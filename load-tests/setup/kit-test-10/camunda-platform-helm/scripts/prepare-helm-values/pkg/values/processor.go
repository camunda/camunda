package values

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-core/pkg/scenarios"
	"scripts/prepare-helm-values/pkg/env"
	"scripts/prepare-helm-values/pkg/placeholders"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"
)

type Options struct {
	ChartPath    string
	Scenario     string
	ScenarioDir  string
	ValuesConfig string
	LicenseKey   string
	Output       string
	OutputDir    string
	Interactive  bool
	EnvFile      string
}

type MissingEnvError struct {
	Missing []string
}

func (e MissingEnvError) Error() string {
	return fmt.Sprintf("missing required environment variables: %s", strings.Join(e.Missing, ", "))
}

func readFile(path string) (string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func writeFile(path, content string) error {
	return os.WriteFile(path, []byte(content), 0o644)
}

func ensureMap(m map[string]any, k string) map[string]any {
	if v, ok := m[k]; ok {
		if mm, ok := v.(map[string]any); ok {
			return mm
		}
	}
	mm := map[string]any{}
	m[k] = mm
	return mm
}

// ResolveValuesFile determines the source values file from options.
func ResolveValuesFile(opts Options) (string, error) {
	sourceValuesFile, err := scenarios.ResolvePath(opts.ScenarioDir, opts.Scenario)
	if err != nil {
		logging.Logger.Debug().Err(err).Msg("Values file not found")
		return "", err
	}

	logging.Logger.Debug().Str("values-file", sourceValuesFile).Msg("Found values file")
	return sourceValuesFile, nil
}

// computeOutputPath determines where to write the processed values file.
func computeOutputPath(sourceValuesFile string, opts Options) (string, error) {
	if opts.Output != "" {
		logging.Logger.Debug().Str("output", opts.Output).Msg("Using explicit output file")
		return opts.Output, nil
	}
	if opts.OutputDir != "" {
		logging.Logger.Debug().Str("output-dir", opts.OutputDir).Msg("Creating output directory if needed")
		// Ensure output directory exists
		if err := os.MkdirAll(opts.OutputDir, 0o755); err != nil {
			return "", fmt.Errorf("create output directory: %w", err)
		}
		// Use the original filename in the output directory
		filename := filepath.Base(sourceValuesFile)
		outputPath := filepath.Join(opts.OutputDir, filename)
		logging.Logger.Debug().Str("output-path", outputPath).Msg("Output path will be")
		return outputPath, nil
	}
	// Default: write in-place
	logging.Logger.Debug().Str("output", sourceValuesFile).Msg("Writing in-place to")
	return sourceValuesFile, nil
}

// Process performs substitution and optional license injection, writing once to disk and
// returning the output path and final content as a string.
func Process(valuesFile string, opts Options) (string, string, error) {
	logging.Logger.Debug().Str("values-file", valuesFile).Msg("Starting values processing for")

	// Build overlay env from JSON config (stringified)
	configEnv := map[string]string{}
	if opts.ValuesConfig != "" && opts.ValuesConfig != "{}" {
		logging.Logger.Debug().Msg("Parsing values-config JSON")
		var m map[string]any
		if err := json.Unmarshal([]byte(opts.ValuesConfig), &m); err != nil {
			logging.Logger.Debug().Err(err).Msg("Failed to parse values-config")
			return "", "", err
		}
		for k, v := range m {
			configEnv[k] = fmt.Sprintf("%v", v)
		}
		logging.Logger.Debug().Int("count", len(configEnv)).Msg("Loaded config values from values-config")
	}

	logging.Logger.Debug().Str("values-file", valuesFile).Msg("Reading values file")
	content, err := readFile(valuesFile)
	if err != nil {
		logging.Logger.Debug().Err(err).Msg("Failed to read values file")
		return "", "", err
	}
	logging.Logger.Debug().Int("bytes", len(content)).Msg("Read bytes from values file")

	// Find required placeholders and validate presence (unset is an error; empty is allowed)
	logging.Logger.Debug().Msg("Scanning for placeholders in values file")
	ph := placeholders.Find(content)
	logging.Logger.Debug().Int("count", len(ph)).Msg("Found unique placeholders to substitute")

	var missing []string
	getVal := func(name string) (string, bool) {
		if v, ok := configEnv[name]; ok {
			return v, true
		}
		v, ok := os.LookupEnv(name)
		return v, ok
	}

	// Log substitutions
	if len(ph) > 0 {
		logging.Logger.Info().Msg("Substituting environment variables:")
		for _, p := range ph {
			if val, ok := getVal(p); ok {
				displayVal := val
				upper := strings.ToUpper(p)
				if strings.Contains(upper, "KEY") || strings.Contains(upper, "SECRET") || strings.Contains(upper, "PASSWORD") || strings.Contains(upper, "TOKEN") {
					displayVal = "***"
				}
				logging.Logger.Info().Str("var", p).Str("value", displayVal).Msg("")
			}
		}
	}

	// Check for missing variables and prompt if interactive
	for _, p := range ph {
		if _, ok := getVal(p); !ok {
			if opts.Interactive {
				// Try to guess a default or just empty
				defVal := ""
				val, err := env.Prompt(p, defVal)
				if err != nil {
					logging.Logger.Error().Err(err).Msg("Failed to read input")
					missing = append(missing, p)
					continue
				}
				if val != "" {
					// Set in current environment so subsequent lookups find it
					os.Setenv(p, val)
					// Also persist to .env file if configured
					if opts.EnvFile != "" {
						if err := env.Append(opts.EnvFile, p, val); err != nil {
							logging.Logger.Warn().Err(err).Msg("Failed to append to .env file")
						} else {
							logging.Logger.Info().Msg("Saved to .env file")
						}
					}
					continue
				}
			}
			missing = append(missing, p)
		}
	}
	if len(missing) > 0 {
		sort.Strings(missing)
		logging.Logger.Debug().Int("count", len(missing)).Msg("Missing required environment variables")
		return "", "", MissingEnvError{Missing: missing}
	}
	logging.Logger.Debug().Msg("All required environment variables are present")

	// Perform substitution using os.Expand to support both $VAR and ${VAR} consistently
	logging.Logger.Debug().Msg("Performing placeholder substitution")
	content = os.Expand(content, func(name string) string {
		if v, ok := getVal(name); ok {
			return v
		}
		// This should not happen (validated above), but keep safe fallback
		return ""
	})
	logging.Logger.Debug().Msg("Placeholder substitution complete")

	// Optional license injection performed in-memory on substituted content
	if opts.LicenseKey != "" {
		logging.Logger.Debug().Msg("Injecting license key into global.license.key")
		var doc map[string]any
		if err := yaml.Unmarshal([]byte(content), &doc); err != nil {
			logging.Logger.Debug().Err(err).Msg("Failed to unmarshal YAML for license injection")
			return "", "", err
		}
		global := ensureMap(doc, "global")
		license := ensureMap(global, "license")
		license["key"] = opts.LicenseKey
		out, err := yaml.Marshal(doc)
		if err != nil {
			logging.Logger.Debug().Err(err).Msg("Failed to marshal YAML after license injection")
			return "", "", err
		}
		content = string(out)
		logging.Logger.Debug().Msg("License key injected successfully")
	} else {
		logging.Logger.Debug().Msg("No license key provided, skipping injection")
	}

	// Determine output path
	logging.Logger.Debug().Msg("Determining output path")
	outputPath, err := computeOutputPath(valuesFile, opts)
	if err != nil {
		logging.Logger.Debug().Err(err).Msg("Failed to compute output path")
		return "", "", err
	}

	// Single write to disk at the end
	logging.Logger.Debug().Str("output-path", outputPath).Msg("Writing processed values to")
	if err := writeFile(outputPath, content); err != nil {
		logging.Logger.Debug().Err(err).Msg("Failed to write output file")
		return "", "", err
	}
	logging.Logger.Debug().Int("bytes", len(content)).Str("output-path", outputPath).Msg("Successfully wrote bytes to")
	return outputPath, content, nil
}

// IsMissingEnv returns (true, names) if err is a MissingEnvError.
func IsMissingEnv(err error) (bool, []string) {
	var me MissingEnvError
	if errors.As(err, &me) {
		return true, me.Missing
	}
	return false, nil
}
