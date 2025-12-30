package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"

	"scripts/camunda-core/pkg/completion"
	"scripts/camunda-core/pkg/logging"
	"scripts/prepare-helm-values/pkg/env"
	"scripts/prepare-helm-values/pkg/values"
)

func Execute() {
	var (
		chartPath    string
		scenario     string
		valuesConfig string
		licenseKey   string
		output       string
		outputDir    string
		envFile      string
		interactive  bool
		logLevel     string
		noColor      bool
	)

	root := &cobra.Command{
		Use:   "prepare-helm-values",
		Short: "Prepare Helm values file by substituting placeholders and injecting license key",
		Long:  "Reads a scenario values file, validates required environment variables, substitutes $VAR and ${VAR} placeholders, and optionally injects .global.license.key.",
		Example: `
  prepare-helm-values \
    --chart-path ./charts/camunda-platform-8.8 \
    --scenario keycloak-original \
    --values-config '{}' \
    --license-key "$E2E_TESTS_LICENSE_KEY" \
    --output-dir /tmp/prepared-values \
    --log-level info`,
		SilenceUsage:  true,
		SilenceErrors: true,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			if err := logging.Setup(logging.Options{
				UseJSON:      false,
				LevelString:  logLevel,
				ColorEnabled: !noColor && logging.IsTerminal(os.Stdout.Fd()),
			}); err != nil {
				return err
			}
			return nil
		},
		PreRunE: func(cmd *cobra.Command, args []string) error {
			if chartPath == "" || scenario == "" {
				logging.Logger.Error().Msg("Required flags missing: --chart-path and --scenario must be set")
				return fmt.Errorf("missing required flags")
			}
			if output != "" && outputDir != "" {
				logging.Logger.Error().Msg("Cannot specify both --output and --output-dir")
				return fmt.Errorf("conflicting output flags")
			}
			// Try to load .env file
			if envFile != "" {
				_ = env.Load(envFile)
			} else {
				// Try default locations if not specified
				_ = env.Load(".env")
			}
			logging.Logger.Debug().Str("chart-path", chartPath).Str("scenario", scenario).Msg("Flags OK")
			return nil
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			opts := values.Options{
				ChartPath:    chartPath,
				Scenario:     scenario,
				ScenarioDir:  filepath.Join(chartPath, "test", "integration", "scenarios", "chart-full-setup"),
				ValuesConfig: valuesConfig,
				LicenseKey:   licenseKey,
				Output:       output,
				OutputDir:    outputDir,
				Interactive:  interactive,
				EnvFile:      envFile,
			}

			if opts.EnvFile == "" {
				opts.EnvFile = ".env" // Default for persistence
			}

			valuesFile, err := values.ResolveValuesFile(opts)
			if err != nil {
				logging.Logger.Error().Err(err).Msg("Values file not found or inaccessible")
				return err
			}

			logging.Logger.Info().Str("chart-path", chartPath).Msg("Using chart path")
			logging.Logger.Info().Str("scenario", scenario).Msg("Scenario")
			logging.Logger.Debug().Str("values-file", valuesFile).Msg("Source values file")

			outputPath, content, err := values.Process(valuesFile, opts)
			if err != nil {
				if missing, names := values.IsMissingEnv(err); missing {
					logging.Logger.Error().Msg("Missing required environment variables for substitution:")
					for _, v := range names {
						fmt.Printf("   - %s\n", v)
					}
					os.Exit(3)
				}
				logging.Logger.Error().Err(err).Msg("Processing failed")
				return err
			}

			logging.Logger.Info().Str("output", outputPath).Msg("Prepared values file")
			if outputDir == "" {
				// Only print to stdout if not writing to output-dir
				fmt.Print(content)
			}
			return nil
		},
	}

	root.Flags().StringVar(&chartPath, "chart-path", "", "Root chart path used to resolve scenarios dir (required)")
	root.Flags().StringVar(&scenario, "scenario", "", "Scenario name (required)")
	completion.RegisterScenarioCompletion(root, "scenario", "chart-path")
	root.Flags().StringVar(&valuesConfig, "values-config", "", "JSON config string for env injection; \"{}\" or empty = skip")
	root.Flags().StringVar(&licenseKey, "license-key", os.Getenv("E2E_TESTS_LICENSE_KEY"), "License key to inject; defaults to $E2E_TESTS_LICENSE_KEY")
	root.Flags().StringVar(&output, "output", "", "Output file path (defaults to scenario values file in-place)")
	root.Flags().StringVar(&outputDir, "output-dir", "", "Output directory path (writes with scenario-based filename)")
	root.Flags().StringVar(&envFile, "env-file", "", "Path to .env file (defaults to .env in current dir)")
	root.Flags().BoolVar(&interactive, "interactive", true, "Enable interactive prompts for missing variables")
	root.Flags().StringVar(&logLevel, "log-level", "info", "Log level: trace, debug, info, warn, error")
	root.Flags().BoolVar(&noColor, "no-color", false, "Disable colored output")
	_ = root.MarkFlagRequired("chart-path")
	_ = root.MarkFlagRequired("scenario")

	if err := root.Execute(); err != nil {
		os.Exit(1)
	}
}
