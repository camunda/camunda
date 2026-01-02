package cmd

import (
	"context"
	"fmt"
	"os"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-core/pkg/scenarios"
	"scripts/deploy-camunda/config"
	"scripts/deploy-camunda/deploy"
	"scripts/deploy-camunda/format"
	"scripts/prepare-helm-values/pkg/env"
	"strings"

	"github.com/spf13/cobra"
)

var (
	// Global flags
	configFile string
	flags      config.RuntimeFlags
)

// NewRootCommand creates the root command.
func NewRootCommand() *cobra.Command {
	rootCmd := &cobra.Command{
		Use:   "deploy-camunda",
		Short: "Deploy Camunda Platform with prepared Helm values",
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			// Skip for config and completion subcommands
			if cmd != nil {
				if cmd.Name() == "config" || (cmd.Parent() != nil && cmd.Parent().Name() == "config") {
					return nil
				}
				if cmd.Name() == "completion" ||
					cmd.Name() == cobra.ShellCompRequestCmd ||
					cmd.Name() == cobra.ShellCompNoDescRequestCmd {
					return nil
				}
			}

			// Load .env file
			if flags.EnvFile != "" {
				_ = env.Load(flags.EnvFile)
			} else {
				_ = env.Load(".env")
			}

			// Load config and merge with flags
			if _, err := config.LoadAndMerge(configFile, true, &flags); err != nil {
				return err
			}

			// Validate merged configuration
			if err := config.Validate(&flags); err != nil {
				return err
			}

			// Validate chartPath exists
			if strings.TrimSpace(flags.ChartPath) != "" {
				if fi, err := os.Stat(flags.ChartPath); err != nil || !fi.IsDir() {
					return fmt.Errorf("resolved chart path %q does not exist or is not a directory; set --repo-root/--chart/--version or --chart-path explicitly", flags.ChartPath)
				}
			}

			return nil
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			// Setup logging
			if err := logging.Setup(logging.Options{
				LevelString:  flags.LogLevel,
				ColorEnabled: logging.IsTerminal(os.Stdout.Fd()),
			}); err != nil {
				return err
			}

			// Log flags
			format.PrintFlags(cmd.Flags())

			// Execute deployment
			return deploy.Execute(context.Background(), &flags)
		},
	}

	// Persistent flags
	rootCmd.PersistentFlags().StringVarP(&configFile, "config", "F", "", "Path to config file (.camunda-deploy.yaml or ~/.config/camunda/deploy.yaml)")

	// Deployment flags
	f := rootCmd.Flags()
	f.StringVar(&flags.ChartPath, "chart-path", "", "Path to the Camunda chart directory")
	f.StringVarP(&flags.Chart, "chart", "c", "", "Chart name")
	f.StringVarP(&flags.ChartVersion, "version", "v", "", "Chart version (only valid with --chart; not allowed with --chart-path)")
	f.StringVarP(&flags.Namespace, "namespace", "n", "", "Kubernetes namespace")
	f.StringVarP(&flags.Release, "release", "r", "", "Helm release name")
	f.StringVarP(&flags.Scenario, "scenario", "s", "", "The name of the scenario to deploy (comma-separated for parallel deployment)")
	f.StringVar(&flags.ScenarioPath, "scenario-path", "", "Path to scenario files")
	f.StringVar(&flags.Auth, "auth", "keycloak", "Auth scenario")
	f.StringVar(&flags.Platform, "platform", "gke", "Target platform: gke, rosa, eks")
	f.StringVarP(&flags.LogLevel, "log-level", "l", "info", "Log level")
	f.BoolVar(&flags.SkipDependencyUpdate, "skip-dependency-update", true, "Skip Helm dependency update")
	f.BoolVar(&flags.ExternalSecrets, "external-secrets", true, "Enable external secrets")
	f.StringVar(&flags.KeycloakHost, "keycloak-host", "keycloak-24-9-0.ci.distro.ultrawombat.com", "Keycloak external host")
	f.StringVar(&flags.KeycloakProtocol, "keycloak-protocol", "https", "Keycloak protocol")
	f.StringVar(&flags.KeycloakRealm, "keycloak-realm", "", "Keycloak realm name (auto-generated if not specified)")
	f.StringVar(&flags.OptimizeIndexPrefix, "optimize-index-prefix", "", "Optimize Elasticsearch index prefix (auto-generated if not specified)")
	f.StringVar(&flags.OrchestrationIndexPrefix, "orchestration-index-prefix", "", "Orchestration Elasticsearch index prefix (auto-generated if not specified)")
	f.StringVar(&flags.TasklistIndexPrefix, "tasklist-index-prefix", "", "Tasklist Elasticsearch index prefix (auto-generated if not specified)")
	f.StringVar(&flags.OperateIndexPrefix, "operate-index-prefix", "", "Operate Elasticsearch index prefix (auto-generated if not specified)")
	f.StringVar(&flags.RepoRoot, "repo-root", "", "Repository root path")
	f.StringVar(&flags.Flow, "flow", "install", "Flow type")
	f.StringVar(&flags.EnvFile, "env-file", "", "Path to .env file (defaults to .env in current dir)")
	f.BoolVar(&flags.Interactive, "interactive", true, "Enable interactive prompts for missing variables")
	f.StringVar(&flags.VaultSecretMapping, "vault-secret-mapping", "", "Vault secret mapping content")
	f.BoolVar(&flags.AutoGenerateSecrets, "auto-generate-secrets", false, "Auto-generate certain secrets for testing purposes")
	f.BoolVar(&flags.DeleteNamespaceFirst, "delete-namespace", false, "Delete the namespace first, then deploy")
	f.StringVar(&flags.DockerUsername, "docker-username", "", "Docker registry username")
	f.StringVar(&flags.DockerPassword, "docker-password", "", "Docker registry password")
	f.BoolVar(&flags.EnsureDockerRegistry, "ensure-docker-registry", false, "Ensure Docker registry secret is created")
	f.BoolVar(&flags.RenderTemplates, "render-templates", false, "Render manifests to a directory instead of installing")
	f.StringVar(&flags.RenderOutputDir, "render-output-dir", "", "Output directory for rendered manifests (defaults to ./rendered/<release>)")
	f.StringSliceVar(&flags.ExtraValues, "extra-values", nil, "Additional Helm values files to apply last (comma-separated or repeatable)")
	f.StringVar(&flags.ValuesPreset, "values-preset", "", "Shortcut to append values-<preset>.yaml from chartPath if present (e.g. latest, enterprise)")
	f.StringVar(&flags.IngressSubdomain, "ingress-subdomain", "", "Ingress subdomain (appended to ."+config.DefaultIngressBaseDomain+")")
	f.StringVar(&flags.IngressHostname, "ingress-hostname", "", "Full ingress hostname (overrides --ingress-subdomain)")
	f.IntVar(&flags.Timeout, "timeout", 5, "Timeout in minutes for Helm deployment")

	// Register completions using config-aware completion function
	registerScenarioCompletion(rootCmd, "scenario")
	registerScenarioCompletion(rootCmd, "auth")

	return rootCmd
}

// registerScenarioCompletion adds tab completion for scenario-related flags.
// It loads the config file and merges with CLI flags to resolve the scenario path.
// Supports comma-separated multi-select for the scenario flag.
func registerScenarioCompletion(cmd *cobra.Command, flagName string) {
	_ = cmd.RegisterFlagCompletionFunc(flagName, func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		// First check CLI flags
		scenarioPath, _ := cmd.Flags().GetString("scenario-path")
		if scenarioPath == "" {
			// Fall back to config file - create temporary flags with CLI values and merge config
			var tempFlags config.RuntimeFlags
			tempFlags.ScenarioPath, _ = cmd.Flags().GetString("scenario-path")
			tempFlags.ChartPath, _ = cmd.Flags().GetString("chart-path")

			if _, err := config.LoadAndMerge(configFile, false, &tempFlags); err == nil {
				scenarioPath = tempFlags.ScenarioPath
			}
		}

		if scenarioPath == "" {
			return cobra.AppendActiveHelp(nil, "Please specify --scenario-path or configure scenarioRoot in your deployment config"), cobra.ShellCompDirectiveNoFileComp
		}

		list, err := scenarios.List(scenarioPath)
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		// Handle comma-separated multi-select
		// Parse already selected scenarios and filter them out
		var prefix string
		var alreadySelected []string
		if idx := strings.LastIndex(toComplete, ","); idx >= 0 {
			prefix = toComplete[:idx+1]
			alreadySelected = strings.Split(toComplete[:idx], ",")
		}

		// Build set of already selected scenarios for fast lookup
		selected := make(map[string]bool)
		for _, s := range alreadySelected {
			selected[strings.TrimSpace(s)] = true
		}

		// Filter out already selected and prepend prefix
		var completions []string
		for _, s := range list {
			if !selected[s] {
				completions = append(completions, prefix+s)
			}
		}

		// Use NoSpace directive to allow continuing with comma for multi-select
		return completions, cobra.ShellCompDirectiveNoFileComp | cobra.ShellCompDirectiveNoSpace
	})
}

// Execute runs the root command.
func Execute() error {
	rootCmd := NewRootCommand()
	rootCmd.AddCommand(newCompletionCommand(rootCmd))
	rootCmd.AddCommand(newConfigCommand())
	return rootCmd.Execute()
}
