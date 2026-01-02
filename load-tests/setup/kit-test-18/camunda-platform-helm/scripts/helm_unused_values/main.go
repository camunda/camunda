package main

import (
	"fmt"
	"os"
	"path/filepath"

	"camunda.com/helmunusedvalues/pkg/config"
	"camunda.com/helmunusedvalues/pkg/output"
	"camunda.com/helmunusedvalues/pkg/patterns"
	"camunda.com/helmunusedvalues/pkg/search"
	"camunda.com/helmunusedvalues/pkg/utils"
	"camunda.com/helmunusedvalues/pkg/values"

	"github.com/schollz/progressbar/v3"
	"github.com/spf13/cobra"
)

func main() {
	// Initialize configuration
	cfg := config.New()

	// Create root command
	rootCmd := &cobra.Command{
		Use:   "helm-unused-values [templates_dir]",
		Short: "Check for unused values in Helm charts",
		Long: `A tool to identify values defined in values.yaml that are not used in templates.
Performance note: If ripgrep (rg) is installed, it will be used for faster searching.
Progress indicators are displayed during long-running operations.`,
		Args: cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			// Set templates directory
			cfg.TemplatesDir = args[0]

			// Run the analyzer
			if err := run(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "Error: %v\n", err)
				os.Exit(1)
			}
		},
	}

	// Add command line flags
	rootCmd.Flags().BoolVar(&cfg.NoColors, "no-colors", false, "Disable colored output")
	rootCmd.Flags().BoolVar(&cfg.ShowAllKeys, "show-all-keys", false, "Show all keys (used and unused), not just unused ones")
	rootCmd.Flags().BoolVar(&cfg.JSONOutput, "json", false, "Output results in JSON format (useful for CI)")
	rootCmd.Flags().IntVar(&cfg.ExitCodeOnUnused, "exit-code", 0, "Set exit code when unused values are found (default: 0)")
	rootCmd.Flags().BoolVar(&cfg.QuietMode, "quiet", false, "Suppress all output except results and errors")
	rootCmd.Flags().StringVar(&cfg.FilterPattern, "filter", "", "Only show keys that match the specified pattern (works with --show-all-keys)")
	rootCmd.Flags().BoolVar(&cfg.Debug, "debug", false, "Enable verbose debug logging")
	rootCmd.Flags().StringVar(&cfg.SearchTool, "search-tool", "", "Search tool to use: 'ripgrep' or 'grep' (default: use ripgrep if available)")
	rootCmd.Flags().IntVar(&cfg.Parallelism, "parallelism", 0, "Number of parallel workers (0 = auto based on CPU cores)")

	// Execute command
	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
}

func run(cfg *config.Config) error {
	if cfg.JSONOutput {
		cfg.NoColors = true
		cfg.QuietMode = true
	}
	display := output.NewDisplay(cfg.NoColors, cfg.QuietMode, cfg.Debug)
	showProgress := !cfg.QuietMode && !cfg.JSONOutput
	if err := utils.ValidateDirectory(cfg.TemplatesDir); err != nil {
		return fmt.Errorf("invalid templates directory: %w", err)
	}

	depOk, missing := utils.CheckDependencies()
	if !depOk {
		display.PrintError(fmt.Sprintf("Missing required dependencies: %v", missing))
		return fmt.Errorf("missing required dependencies")
	}

	ripgrepAvailable := utils.DetectRipgrep()

	switch cfg.SearchTool {
	case "grep":
		cfg.UseRipgrep = false
		display.PrintInfo("Using grep as specified")
	case "ripgrep":
		if !ripgrepAvailable {
			display.PrintWarning("Ripgrep was specified but not found, falling back to grep")
			cfg.UseRipgrep = false
		} else {
			cfg.UseRipgrep = true
			display.PrintSuccess("Using ripgrep as specified")
		}
	default:
		// Auto-detect
		cfg.UseRipgrep = ripgrepAvailable
		if cfg.UseRipgrep {
			display.PrintSuccess("Using ripgrep for faster searching")
		} else {
			display.PrintWarning("Ripgrep not found, using grep instead")
		}
	}

	patternRegistry := patterns.New()
	if err := patternRegistry.RegisterBuiltins(); err != nil {
		return fmt.Errorf("failed to register patterns: %w", err)
	}
	defer patternRegistry.CleanUp()

	keyExtractor := values.NewExtractor(display)

	valuesFile := filepath.Join(cfg.TemplatesDir, "..", "values.yaml")
	if err := utils.ValidateFile(valuesFile); err != nil {
		return fmt.Errorf("invalid values file: %w", err)
	}

	if !cfg.QuietMode {
		display.PrintInfo("Extracting keys from values.yaml...")
	}

	var keys []string
	var err error

	if showProgress {
		bar := progressbar.NewOptions(1,
			progressbar.OptionEnableColorCodes(!cfg.NoColors),
			progressbar.OptionSetWidth(50),
			progressbar.OptionSetDescription("Parsing YAML file..."),
			progressbar.OptionShowCount(),
			progressbar.OptionUseANSICodes(!cfg.NoColors),
			progressbar.OptionSetPredictTime(true),
			progressbar.OptionSpinnerType(14),
		)

		keys, err = keyExtractor.ExtractKeysWithProgress(valuesFile, bar)
	} else {
		keys, err = keyExtractor.ExtractKeys(valuesFile)
	}

	if err != nil {
		return fmt.Errorf("extract values keys: %w", err)
	}

	if cfg.FilterPattern != "" {
		display.PrintInfo(fmt.Sprintf("Filtering results to only show keys matching: %s", cfg.FilterPattern))
		keys = keyExtractor.FilterKeys(keys, cfg.FilterPattern)
	}

	// Report total keys found
	display.PrintWarning(fmt.Sprintf("\nTotal keys found: %d", len(keys)))

	// Create finder
	finder := search.NewFinder(cfg.TemplatesDir, patternRegistry, cfg.UseRipgrep, display)

	// Set parallelism if configured
	if cfg.Parallelism > 0 {
		display.PrintInfo(fmt.Sprintf("Using %d parallel workers", cfg.Parallelism))
		finder.Parallelism = cfg.Parallelism
	}

	// Analyze key usage
	display.PrintInfo("Analyzing key usage:")
	usages, err := finder.FindUnusedKeys(keys, showProgress)
	if err != nil {
		return fmt.Errorf("find unused keys: %w", err)
	}

	// Create reporter
	reporter := output.NewReporter(display, cfg.JSONOutput, cfg.ShowAllKeys)

	// Report results
	if err := reporter.ReportResults(usages); err != nil {
		return fmt.Errorf("report results: %w", err)
	}

	// Calculate unused keys
	unusedKeys := output.FilterByUsageType(usages, "unused")
	parentKeys := output.FilterByUsageType(usages, "parent")
	totalUnused := len(unusedKeys) + len(parentKeys)

	// Exit with appropriate code if unused keys found
	if totalUnused > 0 && cfg.ExitCodeOnUnused != 0 {
		display.DebugLog(fmt.Sprintf("Exiting with code %d (unused keys found)", cfg.ExitCodeOnUnused))
		os.Exit(cfg.ExitCodeOnUnused)
	}

	return nil
}
