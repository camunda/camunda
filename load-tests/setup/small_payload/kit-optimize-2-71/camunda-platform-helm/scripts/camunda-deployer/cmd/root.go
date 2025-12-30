package cmd

import (
	"context"
	"fmt"
	"os"
	"scripts/camunda-core/pkg/completion"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-deployer/pkg/deployer"
	"scripts/camunda-deployer/pkg/types"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

var (
	// Global/logging flags
	logLevel   string
	jsonLog    bool
	noColor    bool
	logCompact bool

	// Deployment flags
	chart                  string
	release                string
	namespace              string
	scenarioDir            string
	renderTemplates        bool
	renderOutputDir        string
	noIncludeCRDs          bool
	dockerUsername         string
	dockerPassword         string
	ingressHost            string
	wait                   bool
	atomic                 bool
	timeout                time.Duration
	platform               string
	namespacePrefix        string
	repoRoot               string
	kubeconfig             string
	kubeContext            string
	scenarioCSV            string
	auth                   string
	ensureDockerRegistry   bool
	skipDockerLogin        bool
	skipDependencyUpdate   bool
	applyIntegrationCreds  bool
	externalSecretsEnabled bool
	ttl                    string
	loadKeycloakRealm      bool
	keycloakRealmName      string
	flow                   string
	vaultSecretPath        string
)

var rootCmd = &cobra.Command{
	Use:   "camunda-deployer",
	Short: "Deploy the Camunda Helm chart to Kubernetes",
	Long: `Deploy the Camunda Platform Helm chart with values layering.

Required flags: --chart, --release, --namespace, --scenario-dir

Examples:
  # Basic deployment with scenario
  camunda-deployer --chart ./charts/camunda-platform-8.8 --release my-release --namespace my-ns --scenario-dir ./charts/camunda-platform-8.8/test/integration/scenarios/chart-full-setup --scenario qa-license

  # With auth and platform
  camunda-deployer --chart ./charts/camunda-platform-8.8 --release test --namespace test-ns --scenario-dir ./scenarios --scenario qa-license --auth keycloak --platform gke`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		level := strings.ToLower(strings.TrimSpace(logLevel))
		if level == "" {
			level = "info"
		}
		if err := logging.Setup(logging.Options{
			UseJSON:      jsonLog,
			LevelString:  level,
			ColorEnabled: !noColor && isTTY(os.Stdout.Fd()),
		}); err != nil {
			return err
		}
		logging.SetCompact(logCompact)
		return nil
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx := cmd.Context()
		if ctx == nil {
			ctx = context.Background()
		}
		
		// Pass nil for commonFiles to let BuildValuesList discover common files from ../common/
		values, err := deployer.BuildValuesList(scenarioDir, splitCSV(scenarioCSV), auth, false, false, nil, nil)
		if err != nil {
			return err
		}
		
		opts := types.Options{
			ChartPath:              chart,
			ReleaseName:            release,
			Namespace:              namespace,
			Kubeconfig:             kubeconfig,
			KubeContext:            kubeContext,
			RenderTemplates:        renderTemplates,
			RenderOutputDir:        renderOutputDir,
			IncludeCRDs:            !noIncludeCRDs,
			Wait:                   wait,
			Atomic:                 atomic,
			Timeout:                timeout,
			IngressHost:            ingressHost,
			ValuesFiles:            values,
			EnsureDockerRegistry:   ensureDockerRegistry,
			SkipDockerLogin:        skipDockerLogin,
			SkipDependencyUpdate:   skipDependencyUpdate,
			ApplyIntegrationCreds:  applyIntegrationCreds,
			ExternalSecretsEnabled: externalSecretsEnabled,
			DockerRegistryUsername: dockerUsername,
			DockerRegistryPassword: dockerPassword,
			Platform:               platform,
			NamespacePrefix:        namespacePrefix,
			RepoRoot:               repoRoot,
			Identifier:             fmt.Sprintf("%s-%s", release, time.Now().Format("20060102150405")),
			TTL:                    ttl,
			LoadKeycloakRealm:      loadKeycloakRealm,
			KeycloakRealmName:      keycloakRealmName,
			CIMetadata: types.CIMetadata{
				Flow: flow,
			},
			VaultSecretPath: vaultSecretPath,
		}
		return deployer.Deploy(ctx, opts)
	},
	SilenceUsage:  true,
	SilenceErrors: true,
}

func init() {
	// Logging flags
	rootCmd.PersistentFlags().StringVar(&logLevel, "log-level", "info", "log level: trace|debug|info|warn|error")
	rootCmd.PersistentFlags().BoolVar(&jsonLog, "json", false, "emit JSON logs (disables color)")
	rootCmd.PersistentFlags().BoolVar(&noColor, "no-color", false, "disable colored output")
	rootCmd.PersistentFlags().BoolVar(&logCompact, "compact-logs", true, "use compact prefixes for command output logs")

	// Required flags
	rootCmd.Flags().StringVar(&chart, "chart", "", "path to chart directory (REQUIRED)")
	rootCmd.Flags().StringVar(&release, "release", "", "Helm release name (REQUIRED)")
	rootCmd.Flags().StringVar(&namespace, "namespace", "", "Kubernetes namespace (REQUIRED)")
	rootCmd.Flags().StringVar(&scenarioDir, "scenario-dir", "", "directory containing scenario values files (REQUIRED)")
	rootCmd.MarkFlagRequired("chart")
	rootCmd.MarkFlagRequired("release")
	rootCmd.MarkFlagRequired("namespace")
	rootCmd.MarkFlagRequired("scenario-dir")

	// Scenario flags
	rootCmd.Flags().StringVar(&scenarioCSV, "scenario", "", "scenario name or comma-separated list (e.g., qa-license,opensearch)")
	completion.RegisterScenarioCompletion(rootCmd, "scenario", "chart")
	rootCmd.Flags().StringVar(&auth, "auth", "", "auth scenario to layer before main scenarios (e.g., keycloak)")
	completion.RegisterScenarioCompletion(rootCmd, "auth", "chart")

	// Deployment behavior flags (with defaults shown)
	rootCmd.Flags().BoolVar(&wait, "wait", true, "wait for resources to be ready")
	rootCmd.Flags().BoolVar(&atomic, "atomic", true, "rollback on failure")
	rootCmd.Flags().DurationVar(&timeout, "timeout", 15*time.Minute, "Helm operation timeout")

	// Render-only behavior
	rootCmd.Flags().BoolVar(&renderTemplates, "render-templates", false, "render manifests to a directory instead of installing")
	rootCmd.Flags().StringVar(&renderOutputDir, "render-output-dir", "", "output directory for rendered manifests (defaults to ./rendered/<release>)")
	rootCmd.Flags().BoolVar(&noIncludeCRDs, "no-include-crds", false, "do not include CRDs in rendered output (by default CRDs are included)")

	// Platform-specific flags
	rootCmd.Flags().StringVar(&platform, "platform", "", "target platform for external secrets: gke, rosa, or eks")
	rootCmd.Flags().StringVar(&namespacePrefix, "namespace-prefix", "", "namespace prefix (used for EKS secrets copy)")
	rootCmd.Flags().StringVar(&repoRoot, "repo-root", "", "repository root path for manifest files")

	// Kubernetes connection flags
	rootCmd.Flags().StringVar(&kubeconfig, "kubeconfig", "", "path to kubeconfig file (defaults to KUBECONFIG env or ~/.kube/config)")
	rootCmd.Flags().StringVar(&kubeContext, "kube-context", "", "Kubernetes context name (defaults to current context)")

	// Ingress configuration
	rootCmd.Flags().StringVar(&ingressHost, "ingress-host", "", "ingress hostname to configure")

	// Advanced deployment options
	rootCmd.Flags().BoolVar(&ensureDockerRegistry, "ensure-docker-registry", true, "ensure Docker registry secret is created")
	rootCmd.Flags().BoolVar(&skipDockerLogin, "skip-docker-login", false, "skip Docker login (useful when already authenticated)")
	rootCmd.Flags().BoolVar(&skipDependencyUpdate, "skip-dependency-update", true, "skip Helm dependency update (useful when deps already updated)")
	rootCmd.Flags().BoolVar(&applyIntegrationCreds, "apply-integration-creds", true, "apply integration test credentials if present")
	rootCmd.Flags().StringVar(&dockerUsername, "docker-username", "", "Docker registry username (defaults to TEST_DOCKER_USERNAME_CAMUNDA_CLOUD or NEXUS_USERNAME)")
	rootCmd.Flags().StringVar(&dockerPassword, "docker-password", "", "Docker registry password (defaults to TEST_DOCKER_PASSWORD_CAMUNDA_CLOUD or NEXUS_PASSWORD)")
	rootCmd.Flags().BoolVar(&externalSecretsEnabled, "external-secrets-enabled", true, "enable external secrets configuration")
	rootCmd.Flags().StringVar(&ttl, "ttl", "1h", "time-to-live label for namespace cleanup (e.g., 1h, 12h, 24h)")

	// Keycloak configuration
	rootCmd.Flags().StringVar(&keycloakRealmName, "keycloak-realm-name", "", "Keycloak realm name to use (required if --load-keycloak-realm is set)")

	// Vault configuration
	rootCmd.Flags().StringVar(&vaultSecretPath, "vault-secret-path", "", "Path to a Kubernetes Secret YAML file to apply")

	// CI Metadata flags
	rootCmd.Flags().StringVar(&flow, "flow", "install", "deployment flow type (install, upgrade, etc.)")
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		// Logger may not be initialized if error happens before PersistentPreRunE.
		if logging.Initialized {
			logging.Logger.Error().Err(err).Msg("command failed")
		} else {
			_, _ = os.Stderr.WriteString("error: " + err.Error() + "\n")
		}
		os.Exit(1)
	}
}

func isTTY(fd uintptr) bool {
	// Defer import to avoid pulling the package at top-level of this file.
	return logging.IsTerminal(fd)
}

func splitCSV(s string) []string {
	out := []string{}
	for _, p := range strings.Split(s, ",") {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
