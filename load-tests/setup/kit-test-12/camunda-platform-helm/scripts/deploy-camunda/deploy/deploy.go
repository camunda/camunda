package deploy

import (
	"context"
	"crypto/rand"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"scripts/camunda-core/pkg/kube"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-deployer/pkg/deployer"
	"scripts/camunda-deployer/pkg/types"
	"scripts/deploy-camunda/config"
	"scripts/prepare-helm-values/pkg/env"
	"scripts/prepare-helm-values/pkg/values"
	"scripts/vault-secret-mapper/pkg/mapper"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/jwalton/gchalk"
)

// ScenarioContext holds scenario-specific deployment configuration.
type ScenarioContext struct {
	ScenarioName             string
	Namespace                string
	Release                  string
	IngressHost              string
	KeycloakRealm            string
	OptimizeIndexPrefix      string
	OrchestrationIndexPrefix string
	TasklistIndexPrefix      string
	OperateIndexPrefix       string
	TempDir                  string
}

// ScenarioResult holds the result of a scenario deployment.
type ScenarioResult struct {
	Scenario                 string
	Namespace                string
	Release                  string
	IngressHost              string
	KeycloakRealm            string
	OptimizeIndexPrefix      string
	OrchestrationIndexPrefix string
	FirstUserPassword        string
	SecondUserPassword       string
	ThirdUserPassword        string
	KeycloakClientsSecret    string
	Error                    error
}

// PreparedScenario holds the result of values preparation for a scenario,
// ready to be deployed in parallel.
type PreparedScenario struct {
	ScenarioCtx         *ScenarioContext
	ValuesFiles         []string
	VaultSecretPath     string
	TempDir             string
	RealmName           string
	OptimizePrefix      string
	OrchestrationPrefix string
}

// envMutex protects environment variable access during parallel deployments.
var envMutex sync.Mutex

// processCommonValues finds and processes common values files from the common/ sibling directory.
// It processes each file through values.Process() to apply env var substitution and writes to outputDir.
// Returns the list of processed file paths in the output directory.
func processCommonValues(scenarioPath, outputDir, envFile string) ([]string, error) {
	// Common directory is a sibling to the scenario directory
	commonDir := filepath.Join(filepath.Dir(scenarioPath), "..", "common")

	logging.Logger.Debug().
		Str("scenarioPath", scenarioPath).
		Str("commonDir", commonDir).
		Str("outputDir", outputDir).
		Msg("🔍 [processCommonValues] looking for common values directory")

	info, err := os.Stat(commonDir)
	if err != nil || !info.IsDir() {
		logging.Logger.Debug().
			Str("commonDir", commonDir).
			Msg("🔍 [processCommonValues] common directory not found - skipping")
		return nil, nil
	}

	// Collect common values files in order
	var sourceFiles []string

	// First, add predefined common files in order (if they exist)
	for _, fileName := range deployer.CommonValuesFiles {
		p := filepath.Join(commonDir, fileName)
		if _, err := os.Stat(p); err == nil {
			logging.Logger.Debug().
				Str("file", p).
				Msg("🔍 [processCommonValues] found predefined common values file")
			sourceFiles = append(sourceFiles, p)
		}
	}

	// Then, discover any additional values-*.yaml files not in the predefined list
	entries, err := os.ReadDir(commonDir)
	if err != nil {
		logging.Logger.Debug().
			Err(err).
			Str("commonDir", commonDir).
			Msg("⚠️ [processCommonValues] failed to read common directory")
		return sourceFiles, nil
	}

	predefinedSet := make(map[string]bool)
	for _, f := range deployer.CommonValuesFiles {
		predefinedSet[f] = true
	}

	var additionalFiles []string
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if predefinedSet[name] {
			continue
		}
		if strings.HasPrefix(name, "values-") && strings.HasSuffix(name, ".yaml") {
			p := filepath.Join(commonDir, name)
			logging.Logger.Debug().
				Str("file", p).
				Msg("🔍 [processCommonValues] found additional common values file")
			additionalFiles = append(additionalFiles, p)
		}
	}

	// Sort additional files for deterministic ordering
	sort.Strings(additionalFiles)
	sourceFiles = append(sourceFiles, additionalFiles...)

	if len(sourceFiles) == 0 {
		logging.Logger.Debug().
			Str("commonDir", commonDir).
			Msg("🔍 [processCommonValues] no common values files found")
		return nil, nil
	}

	// Process each common file
	var processedFiles []string
	for _, srcFile := range sourceFiles {
		logging.Logger.Debug().
			Str("source", srcFile).
			Str("outputDir", outputDir).
			Msg("⚙️ [processCommonValues] processing common values file")

		opts := values.Options{
			OutputDir: outputDir,
			EnvFile:   envFile,
		}
		if opts.EnvFile == "" {
			opts.EnvFile = ".env"
		}

		outputPath, _, err := values.Process(srcFile, opts)
		if err != nil {
			logging.Logger.Debug().
				Err(err).
				Str("source", srcFile).
				Msg("❌ [processCommonValues] failed to process common values file")
			return nil, fmt.Errorf("failed to process common values file %q: %w", srcFile, err)
		}

		logging.Logger.Debug().
			Str("source", srcFile).
			Str("output", outputPath).
			Msg("✅ [processCommonValues] processed common values file")
		processedFiles = append(processedFiles, outputPath)
	}

	logging.Logger.Debug().
		Strs("processedFiles", processedFiles).
		Int("count", len(processedFiles)).
		Msg("✅ [processCommonValues] all common values files processed")

	return processedFiles, nil
}

// redactDeployOpts returns a copy of deploy options with sensitive fields redacted for logging.
func redactDeployOpts(opts types.Options) map[string]interface{} {
	redacted := "[REDACTED]"
	return map[string]interface{}{
		"chart":                  opts.Chart,
		"chartPath":              opts.ChartPath,
		"version":                opts.Version,
		"releaseName":            opts.ReleaseName,
		"namespace":              opts.Namespace,
		"timeout":                opts.Timeout.String(),
		"wait":                   opts.Wait,
		"atomic":                 opts.Atomic,
		"ingressHost":            opts.IngressHost,
		"valuesFiles":            opts.ValuesFiles,
		"identifier":             opts.Identifier,
		"ttl":                    opts.TTL,
		"ensureDockerRegistry":   opts.EnsureDockerRegistry,
		"dockerRegistryUsername": opts.DockerRegistryUsername,
		"dockerRegistryPassword": func() string { if opts.DockerRegistryPassword != "" { return redacted }; return "" }(),
		"skipDockerLogin":        opts.SkipDockerLogin,
		"skipDependencyUpdate":   opts.SkipDependencyUpdate,
		"applyIntegrationCreds":  opts.ApplyIntegrationCreds,
		"externalSecretsEnabled": opts.ExternalSecretsEnabled,
		"platform":               opts.Platform,
		"repoRoot":               opts.RepoRoot,
		"loadKeycloakRealm":      opts.LoadKeycloakRealm,
		"keycloakRealmName":      opts.KeycloakRealmName,
		"vaultSecretPath":        opts.VaultSecretPath,
		"renderTemplates":        opts.RenderTemplates,
		"renderOutputDir":        opts.RenderOutputDir,
		"includeCRDs":            opts.IncludeCRDs,
		"ciMetadata":             opts.CIMetadata,
	}
}

// generateRandomSuffix creates an 8-character random string.
func generateRandomSuffix() string {
	const chars = "abcdefghijklmnopqrstuvwxyz0123456789"
	result := make([]byte, 8)
	for i := range result {
		num, _ := rand.Int(rand.Reader, big.NewInt(int64(len(chars))))
		result[i] = chars[num.Int64()]
	}
	return string(result)
}

// generateCompactRealmName creates a realm name that fits within Keycloak's 36 character limit.
// Format: {prefix}-{hash} where hash is derived from namespace+scenario+suffix.
func generateCompactRealmName(namespace, scenario, suffix string) string {
	const maxLength = 36

	// Try simple format first: scenario-suffix (e.g., "keycloak-mt-a8x9z3k1")
	simple := fmt.Sprintf("%s-%s", scenario, suffix)
	if len(simple) <= maxLength {
		return simple
	}

	// If scenario name is too long, truncate it and add a short hash for uniqueness
	// Format: {truncated-scenario}-{short-hash}
	// Reserve 9 characters for "-" + 8 char hash
	maxScenarioLen := maxLength - 9

	if len(scenario) > maxScenarioLen {
		scenario = scenario[:maxScenarioLen]
	}

	// Create a short hash from the full identifier for uniqueness
	fullId := fmt.Sprintf("%s-%s-%s", namespace, scenario, suffix)
	hash := fmt.Sprintf("%x", big.NewInt(0).SetBytes([]byte(fullId)).Int64())
	if len(hash) > 8 {
		hash = hash[:8]
	}

	result := fmt.Sprintf("%s-%s", scenario, hash)

	// Final safety check - truncate if still too long
	if len(result) > maxLength {
		result = result[:maxLength]
	}

	return result
}

// captureEnv saves current values of specified environment variables.
func captureEnv(keys []string) map[string]string {
	envVars := make(map[string]string, len(keys))
	for _, key := range keys {
		envVars[key] = os.Getenv(key)
	}
	return envVars
}

// restoreEnv restores environment variables to captured values.
func restoreEnv(envVars map[string]string) {
	for key, val := range envVars {
		if val == "" {
			_ = os.Unsetenv(key)
		} else {
			_ = os.Setenv(key, val)
		}
	}
}

// enhanceScenarioError wraps scenario resolution errors with helpful context.
func enhanceScenarioError(err error, scenario, scenarioPath, chartPath string) error {
	if err == nil {
		return nil
	}

	// Check if it's a "not found" type error
	errStr := err.Error()
	if !strings.Contains(errStr, "not found") && !strings.Contains(errStr, "no such file") {
		return err
	}

	// Try to list available scenarios
	scenarioDir := scenarioPath
	if scenarioDir == "" {
		// Default scenario location
		scenarioDir = filepath.Join(chartPath, "test/integration/scenarios/chart-full-setup")
	}

	var helpMsg strings.Builder
	fmt.Fprintf(&helpMsg, "❌ Scenario %q not found\n\n", scenario)
	fmt.Fprintf(&helpMsg, "Searched in: %s\n", scenarioDir)
	fmt.Fprintf(&helpMsg, "Expected file: values-integration-test-ingress-%s.yaml\n\n", scenario)

	// Try to list available scenarios
	entries, readErr := os.ReadDir(scenarioDir)
	if readErr != nil {
		fmt.Fprintf(&helpMsg, "⚠️  Could not list available scenarios: %v\n\n", readErr)
		fmt.Fprintf(&helpMsg, "Please check:\n")
		fmt.Fprintf(&helpMsg, "  1. The scenario directory exists: %s\n", scenarioDir)
		fmt.Fprintf(&helpMsg, "  2. You have permission to read it\n")
		fmt.Fprintf(&helpMsg, "  3. The --chart-path or --scenario-path flags are set correctly\n")
	} else {
		var availableScenarios []string
		for _, e := range entries {
			name := e.Name()
			if !e.IsDir() && strings.HasPrefix(name, "values-integration-test-ingress-") && strings.HasSuffix(name, ".yaml") {
				// Extract scenario name
				scenarioName := strings.TrimPrefix(name, "values-integration-test-ingress-")
				scenarioName = strings.TrimSuffix(scenarioName, ".yaml")
				availableScenarios = append(availableScenarios, scenarioName)
			}
		}

		if len(availableScenarios) == 0 {
			fmt.Fprintf(&helpMsg, "⚠️  No scenario files found in: %s\n\n", scenarioDir)
			fmt.Fprintf(&helpMsg, "Expected files matching pattern: values-integration-test-ingress-*.yaml\n")
		} else {
			fmt.Fprintf(&helpMsg, "✅ Available scenarios (%d found):\n", len(availableScenarios))
			for _, s := range availableScenarios {
				fmt.Fprintf(&helpMsg, "  • %s\n", s)
			}
			fmt.Fprintf(&helpMsg, "\n💡 Hint: Use --scenario <name> or --scenario <name1>,<name2> for multiple scenarios\n")
		}
	}

	fmt.Fprintf(&helpMsg, "\n📚 Documentation: Check the chart's test/integration/scenarios/ directory\n")
	fmt.Fprintf(&helpMsg, "   for available scenario configurations.\n")

	return fmt.Errorf("%s\n%s", helpMsg.String(), err)
}

// Execute performs the actual Camunda deployment based on the provided flags.
func Execute(ctx context.Context, flags *config.RuntimeFlags) error {
	// Check if we're deploying multiple scenarios in parallel
	if len(flags.Scenarios) > 1 {
		return executeParallelDeployments(ctx, flags)
	}

	// Single scenario deployment (original behavior)
	return executeSingleDeployment(ctx, flags)
}

// executeParallelDeployments deploys multiple scenarios concurrently.
func executeParallelDeployments(ctx context.Context, flags *config.RuntimeFlags) error {
	logging.Logger.Info().
		Int("count", len(flags.Scenarios)).
		Strs("scenarios", flags.Scenarios).
		Msg("Starting parallel deployment of multiple scenarios")

	// Validate all scenarios exist before starting any deployments
	// This provides better error messages and fails fast
	scenarioDir := flags.ScenarioPath
	if scenarioDir == "" {
		scenarioDir = filepath.Join(flags.ChartPath, "test/integration/scenarios/chart-full-setup")
	}

	for _, scenario := range flags.Scenarios {
		// Try to resolve the scenario file
		var filename string
		if strings.HasPrefix(scenario, "values-integration-test-ingress-") && strings.HasSuffix(scenario, ".yaml") {
			filename = scenario
		} else {
			filename = fmt.Sprintf("values-integration-test-ingress-%s.yaml", scenario)
		}

		sourceValuesFile := filepath.Join(scenarioDir, filename)
		if _, err := os.Stat(sourceValuesFile); err != nil {
			// Enhance error with helpful context
			return enhanceScenarioError(err, scenario, flags.ScenarioPath, flags.ChartPath)
		}
	}

	logging.Logger.Info().Msg("All scenarios validated successfully")

	// ============================================================
	// PHASE 1: Prepare all scenarios SEQUENTIALLY
	// This handles interactive prompts and environment variable substitution
	// safely before any parallel execution begins.
	// ============================================================
	logging.Logger.Info().
		Int("count", len(flags.Scenarios)).
		Msg("Phase 1: Preparing values for all scenarios sequentially")

	prepared := make([]*PreparedScenario, 0, len(flags.Scenarios))
	for _, scenario := range flags.Scenarios {
		scenarioCtx := generateScenarioContext(scenario, flags)

		logging.Logger.Info().
			Str("scenario", scenario).
			Str("namespace", scenarioCtx.Namespace).
			Msg("Preparing scenario")

		p, err := prepareScenarioValues(scenarioCtx, flags)
		if err != nil {
			// Cleanup any already-prepared temp directories
			for _, prep := range prepared {
				logging.Logger.Debug().
					Str("dir", prep.TempDir).
					Str("scenario", prep.ScenarioCtx.ScenarioName).
					Msg("🧹 Cleaning up prepared scenario temp dir due to preparation failure")
				os.RemoveAll(prep.TempDir)
			}
			return fmt.Errorf("scenario %q failed during preparation: %w", scenario, err)
		}
		prepared = append(prepared, p)
	}

	logging.Logger.Info().
		Int("count", len(prepared)).
		Msg("Phase 1 complete: All scenarios prepared successfully")

	// ============================================================
	// PHASE 2: Deploy all scenarios IN PARALLEL
	// All interactive prompts and env var substitution is complete,
	// so deployments can safely run concurrently.
	// ============================================================
	logging.Logger.Info().
		Int("count", len(prepared)).
		Msg("Phase 2: Deploying all scenarios in parallel")

	var wg sync.WaitGroup
	resultCh := make(chan *ScenarioResult, len(prepared))

	for _, p := range prepared {
		p := p // capture for closure
		wg.Add(1)
		go func() {
			defer wg.Done()
			// Use original context (not a cancellable one) so failures don't cancel others
			result := executeDeployment(ctx, p, flags)
			resultCh <- result
		}()
	}

	// Wait for all deployments to complete
	wg.Wait()
	close(resultCh)

	// Collect results
	results := make([]*ScenarioResult, 0, len(flags.Scenarios))
	for result := range resultCh {
		results = append(results, result)
	}

	// Print summary
	printMultiScenarioSummary(results)

	// Return error if any scenario failed
	var hasErrors bool
	for _, r := range results {
		if r.Error != nil {
			hasErrors = true
			break
		}
	}

	if hasErrors {
		return fmt.Errorf("one or more scenarios failed deployment")
	}
	return nil
}

// executeSingleDeployment deploys a single scenario (original behavior).
func executeSingleDeployment(ctx context.Context, flags *config.RuntimeFlags) error {
	scenario := flags.Scenarios[0]
	scenarioCtx := generateScenarioContext(scenario, flags)

	// Phase 1: Prepare values
	prepared, err := prepareScenarioValues(scenarioCtx, flags)
	if err != nil {
		return fmt.Errorf("failed to prepare scenario: %w", err)
	}

	// Phase 2: Deploy
	result := executeDeployment(ctx, prepared, flags)

	if result.Error != nil {
		return result.Error
	}

	// Print single deployment summary
	printDeploymentSummary(result.KeycloakRealm, result.OptimizeIndexPrefix, result.OrchestrationIndexPrefix)
	return nil
}

// generateScenarioContext creates a scenario-specific deployment context.
func generateScenarioContext(scenario string, flags *config.RuntimeFlags) *ScenarioContext {
	suffix := generateRandomSuffix()

	// Generate unique identifiers for this scenario
	var realmName, optimizePrefix, orchestrationPrefix, tasklistPrefix, operatePrefix string
	var namespace, release, ingressHost string

	// Use provided values or generate unique ones
	if flags.KeycloakRealm != "" && len(flags.Scenarios) == 1 {
		realmName = flags.KeycloakRealm
	} else {
		// Keycloak realm name has a maximum length of 36 characters
		// Generate a compact name that fits within this limit
		realmName = generateCompactRealmName(flags.Namespace, scenario, suffix)
	}

	if flags.OptimizeIndexPrefix != "" && len(flags.Scenarios) == 1 {
		optimizePrefix = flags.OptimizeIndexPrefix
	} else {
		optimizePrefix = fmt.Sprintf("opt-%s-%s", scenario, suffix)
	}

	if flags.OrchestrationIndexPrefix != "" && len(flags.Scenarios) == 1 {
		orchestrationPrefix = flags.OrchestrationIndexPrefix
	} else {
		orchestrationPrefix = fmt.Sprintf("orch-%s-%s", scenario, suffix)
	}

	if flags.TasklistIndexPrefix != "" && len(flags.Scenarios) == 1 {
		tasklistPrefix = flags.TasklistIndexPrefix
	} else {
		tasklistPrefix = fmt.Sprintf("task-%s-%s", scenario, suffix)
	}

	if flags.OperateIndexPrefix != "" && len(flags.Scenarios) == 1 {
		operatePrefix = flags.OperateIndexPrefix
	} else {
		operatePrefix = fmt.Sprintf("op-%s-%s", scenario, suffix)
	}

	// Generate unique namespace for multi-scenario, but always use "integration" as release name
	// since we never have multiple deployments in the same namespace
	resolvedHost := flags.ResolveIngressHostname()
	if len(flags.Scenarios) > 1 {
		namespace = fmt.Sprintf("%s-%s", flags.Namespace, scenario)
		if resolvedHost != "" {
			ingressHost = fmt.Sprintf("%s-%s", scenario, resolvedHost)
		}
	} else {
		namespace = flags.Namespace
		ingressHost = resolvedHost
	}

	// Always use "integration" as the release name
	release = "integration"

	return &ScenarioContext{
		ScenarioName:             scenario,
		Namespace:                namespace,
		Release:                  release,
		IngressHost:              ingressHost,
		KeycloakRealm:            realmName,
		OptimizeIndexPrefix:      optimizePrefix,
		OrchestrationIndexPrefix: orchestrationPrefix,
		TasklistIndexPrefix:      tasklistPrefix,
		OperateIndexPrefix:       operatePrefix,
	}
}

// prepareScenarioValues processes values files for a scenario and returns a PreparedScenario.
// This function handles all environment variable substitution and interactive prompts,
// making it safe to call sequentially before parallel deployments.
func prepareScenarioValues(scenarioCtx *ScenarioContext, flags *config.RuntimeFlags) (*PreparedScenario, error) {
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Str("keycloakRealm", scenarioCtx.KeycloakRealm).
		Msg("📋 [prepareScenarioValues] ENTRY - starting values preparation")

	logging.Logger.Info().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Msg("Preparing scenario values")

	// Generate identifiers
	realmName := scenarioCtx.KeycloakRealm
	optimizePrefix := scenarioCtx.OptimizeIndexPrefix
	orchestrationPrefix := scenarioCtx.OrchestrationIndexPrefix

	// Create temp directory for values
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("pattern", fmt.Sprintf("camunda-values-%s-*", scenarioCtx.ScenarioName)).
		Msg("📁 [prepareScenarioValues] creating temporary directory for values files")

	tempDir, err := os.MkdirTemp("", fmt.Sprintf("camunda-values-%s-*", scenarioCtx.ScenarioName))
	if err != nil {
		logging.Logger.Debug().
			Err(err).
			Str("scenario", scenarioCtx.ScenarioName).
			Msg("❌ [prepareScenarioValues] FAILED to create temp directory")
		return nil, fmt.Errorf("failed to create temp directory: %w", err)
	}
	scenarioCtx.TempDir = tempDir
	logging.Logger.Debug().Str("dir", tempDir).Str("scenario", scenarioCtx.ScenarioName).Msg("✅ [prepareScenarioValues] temp directory created successfully")

	// Thread-safe environment variable manipulation
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Msg("🔒 [prepareScenarioValues] acquiring environment mutex for values processing")
	envMutex.Lock()
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Msg("✅ [prepareScenarioValues] environment mutex acquired")

	// Set environment variables for prepare-helm-values
	envVarsToCapture := []string{
		"KEYCLOAK_REALM",
		"OPTIMIZE_INDEX_PREFIX",
		"ORCHESTRATION_INDEX_PREFIX",
		"TASKLIST_INDEX_PREFIX",
		"OPERATE_INDEX_PREFIX",
		"CAMUNDA_HOSTNAME",
		"FLOW",
	}
	originalEnv := captureEnv(envVarsToCapture)

	// Ensure environment is restored and mutex is unlocked even on error
	defer func() {
		logging.Logger.Debug().
			Str("scenario", scenarioCtx.ScenarioName).
			Msg("🔄 [prepareScenarioValues] restoring environment and releasing mutex")
		restoreEnv(originalEnv)
		envMutex.Unlock()
	}()

	// Set scenario-specific environment variables
	os.Setenv("KEYCLOAK_REALM", realmName)
	os.Setenv("OPTIMIZE_INDEX_PREFIX", optimizePrefix)
	os.Setenv("ORCHESTRATION_INDEX_PREFIX", orchestrationPrefix)
	if scenarioCtx.TasklistIndexPrefix != "" {
		os.Setenv("TASKLIST_INDEX_PREFIX", scenarioCtx.TasklistIndexPrefix)
	}
	if scenarioCtx.OperateIndexPrefix != "" {
		os.Setenv("OPERATE_INDEX_PREFIX", scenarioCtx.OperateIndexPrefix)
	}
	if scenarioCtx.IngressHost != "" {
		os.Setenv("CAMUNDA_HOSTNAME", scenarioCtx.IngressHost)
	}
	os.Setenv("FLOW", flags.Flow)

	// Set Keycloak environment variables
	if flags.KeycloakHost != "" {
		kcVersionSafe := "24_9_0"
		kcHostVar := fmt.Sprintf("KEYCLOAK_EXT_HOST_%s", kcVersionSafe)
		kcProtoVar := fmt.Sprintf("KEYCLOAK_EXT_PROTOCOL_%s", kcVersionSafe)
		os.Setenv(kcHostVar, flags.KeycloakHost)
		os.Setenv(kcProtoVar, flags.KeycloakProtocol)
	}

	// Helper function to process values files
	processValues := func(scen string) error {
		logging.Logger.Debug().
			Str("scenario", scen).
			Str("chartPath", flags.ChartPath).
			Str("scenarioDir", flags.ScenarioPath).
			Str("outputDir", tempDir).
			Bool("interactive", flags.Interactive).
			Msg("📝 [prepareScenarioValues.processValues] building values options")

		opts := values.Options{
			ChartPath:   flags.ChartPath,
			Scenario:    scen,
			ScenarioDir: flags.ScenarioPath,
			OutputDir:   tempDir,
			Interactive: flags.Interactive,
			EnvFile:     flags.EnvFile,
		}
		if opts.EnvFile == "" {
			opts.EnvFile = ".env"
		}

		file, err := values.ResolveValuesFile(opts)
		if err != nil {
			return enhanceScenarioError(err, scen, flags.ScenarioPath, flags.ChartPath)
		}

		_, _, err = values.Process(file, opts)
		if err != nil {
			return fmt.Errorf("failed to process scenario %q: %w", scen, err)
		}
		logging.Logger.Debug().
			Str("scenario", scen).
			Str("file", file).
			Msg("✅ [prepareScenarioValues.processValues] values file processed successfully")
		return nil
	}

	// Process common values files first (base layer)
	logging.Logger.Debug().
		Str("scenarioPath", flags.ScenarioPath).
		Str("tempDir", tempDir).
		Msg("📋 [prepareScenarioValues] processing common values files")
	processedCommonFiles, err := processCommonValues(flags.ScenarioPath, tempDir, flags.EnvFile)
	if err != nil {
		os.RemoveAll(tempDir) // Cleanup on error
		return nil, fmt.Errorf("failed to process common values: %w", err)
	}

	// Process auth scenario if different from main scenario
	if flags.Auth != "" && flags.Auth != scenarioCtx.ScenarioName {
		logging.Logger.Info().Str("auth", flags.Auth).Str("scenario", scenarioCtx.ScenarioName).Msg("Preparing auth scenario")
		if err := processValues(flags.Auth); err != nil {
			os.RemoveAll(tempDir) // Cleanup on error
			return nil, fmt.Errorf("failed to prepare auth scenario: %w", err)
		}
	}

	// Process main scenario
	logging.Logger.Info().Str("scenario", scenarioCtx.ScenarioName).Msg("Preparing main scenario")
	if err := processValues(scenarioCtx.ScenarioName); err != nil {
		os.RemoveAll(tempDir) // Cleanup on error
		return nil, fmt.Errorf("failed to prepare main scenario: %w", err)
	}

	// Auto-generate secrets if requested
	if flags.AutoGenerateSecrets {
		logging.Logger.Debug().
			Str("scenario", scenarioCtx.ScenarioName).
			Msg("🔑 [prepareScenarioValues] auto-generating test secrets")
		if err := generateTestSecrets(flags.EnvFile); err != nil {
			os.RemoveAll(tempDir) // Cleanup on error
			return nil, fmt.Errorf("failed to generate test secrets: %w", err)
		}
	}

	// Generate vault secrets if auto-generating
	var vaultSecretPath string
	if flags.AutoGenerateSecrets {
		vaultSecretPath = filepath.Join(tempDir, "vault-mapped-secrets.yaml")
		logging.Logger.Info().Str("scenario", scenarioCtx.ScenarioName).Msg("Generating vault secrets")
		mapping := flags.VaultSecretMapping
		if mapping == "" {
			mapping = os.Getenv("vault_secret_mapping")
		}
		if err := mapper.Generate(mapping, "vault-mapped-secrets", vaultSecretPath); err != nil {
			os.RemoveAll(tempDir) // Cleanup on error
			return nil, fmt.Errorf("failed to generate vault secrets: %w", err)
		}
	}

	// Build values files list
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("tempDir", tempDir).
		Msg("📋 [prepareScenarioValues] building values files list")
	vals, err := deployer.BuildValuesList(tempDir, []string{scenarioCtx.ScenarioName}, flags.Auth, false, false, flags.ExtraValues, processedCommonFiles)
	if err != nil {
		os.RemoveAll(tempDir) // Cleanup on error
		return nil, fmt.Errorf("failed to build values list: %w", err)
	}

	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Strs("valuesFiles", vals).
		Int("count", len(vals)).
		Msg("✅ [prepareScenarioValues] EXIT - values preparation completed successfully")

	logging.Logger.Info().
		Str("scenario", scenarioCtx.ScenarioName).
		Int("valuesFilesCount", len(vals)).
		Msg("Scenario values preparation completed")

	return &PreparedScenario{
		ScenarioCtx:         scenarioCtx,
		ValuesFiles:         vals,
		VaultSecretPath:     vaultSecretPath,
		TempDir:             tempDir,
		RealmName:           realmName,
		OptimizePrefix:      optimizePrefix,
		OrchestrationPrefix: orchestrationPrefix,
	}, nil
}

// executeDeployment runs the helm deployment for a prepared scenario.
// This function is safe to run in parallel as it doesn't do any interactive prompts
// or environment variable manipulation that requires mutex protection.
func executeDeployment(ctx context.Context, prepared *PreparedScenario, flags *config.RuntimeFlags) *ScenarioResult {
	startTime := time.Now()
	scenarioCtx := prepared.ScenarioCtx

	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Str("release", scenarioCtx.Release).
		Str("ingressHost", scenarioCtx.IngressHost).
		Strs("valuesFiles", prepared.ValuesFiles).
		Msg("🚀 [executeDeployment] ENTRY - starting deployment")

	result := &ScenarioResult{
		Scenario:                 scenarioCtx.ScenarioName,
		Namespace:                scenarioCtx.Namespace,
		Release:                  scenarioCtx.Release,
		IngressHost:              scenarioCtx.IngressHost,
		KeycloakRealm:            prepared.RealmName,
		OptimizeIndexPrefix:      prepared.OptimizePrefix,
		OrchestrationIndexPrefix: prepared.OrchestrationPrefix,
	}

	// Ensure temp directory is cleaned up when deployment completes
	defer func() {
		logging.Logger.Debug().
			Str("dir", prepared.TempDir).
			Str("scenario", scenarioCtx.ScenarioName).
			Msg("🧹 [executeDeployment] cleaning up temporary directory")
		os.RemoveAll(prepared.TempDir)
	}()

	logging.Logger.Info().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Str("realm", prepared.RealmName).
		Msg("Starting scenario deployment")

	// Determine timeout duration from flags (default to 5 minutes if not set)
	timeoutMinutes := flags.Timeout
	if timeoutMinutes <= 0 {
		timeoutMinutes = 5
	}

	identifier := fmt.Sprintf("%s-%s-%s", scenarioCtx.Release, scenarioCtx.ScenarioName, time.Now().Format("20060102150405"))
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("identifier", identifier).
		Msg("🏷️ [executeDeployment] generated deployment identifier")

	// Build deployment options
	deployOpts := types.Options{
		ChartPath:              flags.ChartPath,
		Chart:                  flags.Chart,
		Version:                flags.ChartVersion,
		ReleaseName:            scenarioCtx.Release,
		Namespace:              scenarioCtx.Namespace,
		Wait:                   true,
		Atomic:                 true,
		Timeout:                time.Duration(timeoutMinutes) * time.Minute,
		ValuesFiles:            prepared.ValuesFiles,
		EnsureDockerRegistry:   flags.EnsureDockerRegistry,
		SkipDependencyUpdate:   flags.SkipDependencyUpdate,
		ExternalSecretsEnabled: flags.ExternalSecrets,
		DockerRegistryUsername: flags.DockerUsername,
		DockerRegistryPassword: flags.DockerPassword,
		Platform:               flags.Platform,
		RepoRoot:               flags.RepoRoot,
		Identifier:             identifier,
		TTL:                    "30m",
		LoadKeycloakRealm:      true,
		KeycloakRealmName:      prepared.RealmName,
		RenderTemplates:        flags.RenderTemplates,
		RenderOutputDir:        flags.RenderOutputDir,
		IncludeCRDs:            true,
		CIMetadata: types.CIMetadata{
			Flow: flags.Flow,
		},
		ApplyIntegrationCreds: true,
		VaultSecretPath:       prepared.VaultSecretPath,
	}

	// Log deployment options (redact sensitive fields)
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Interface("deployOpts", redactDeployOpts(deployOpts)).
		Msg("🚀 [executeDeployment] deployment options configured")

	// Delete namespace first if requested
	if flags.DeleteNamespaceFirst {
		logging.Logger.Info().Str("namespace", scenarioCtx.Namespace).Str("scenario", scenarioCtx.ScenarioName).Msg("Deleting namespace prior to deployment as requested")
		if err := deleteNamespace(ctx, scenarioCtx.Namespace); err != nil {
			logging.Logger.Debug().
				Err(err).
				Str("namespace", scenarioCtx.Namespace).
				Str("scenario", scenarioCtx.ScenarioName).
				Msg("❌ [executeDeployment] FAILED to delete namespace")
			result.Error = fmt.Errorf("failed to delete namespace %q: %w", scenarioCtx.Namespace, err)
			return result
		}
		logging.Logger.Debug().
			Str("namespace", scenarioCtx.Namespace).
			Str("scenario", scenarioCtx.ScenarioName).
			Msg("✅ [executeDeployment] namespace deleted successfully")
	}

	// Execute deployment
	deployStartTime := time.Now()
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Str("release", scenarioCtx.Release).
		Time("startTime", deployStartTime).
		Msg("🚀 [executeDeployment] initiating helm deployment")

	if err := deployer.Deploy(ctx, deployOpts); err != nil {
		deployDuration := time.Since(deployStartTime)
		logging.Logger.Debug().
			Err(err).
			Str("scenario", scenarioCtx.ScenarioName).
			Str("namespace", scenarioCtx.Namespace).
			Dur("deployDuration", deployDuration).
			Msg("❌ [executeDeployment] DEPLOYMENT FAILED")
		result.Error = err
		return result
	}

	deployDuration := time.Since(deployStartTime)
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Dur("deployDuration", deployDuration).
		Msg("✅ [executeDeployment] helm deployment completed successfully")

	// Capture credentials from environment
	result.FirstUserPassword = os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD")
	result.SecondUserPassword = os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD")
	result.ThirdUserPassword = os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD")
	result.KeycloakClientsSecret = os.Getenv("DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET")

	totalDuration := time.Since(startTime)
	logging.Logger.Debug().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Str("release", scenarioCtx.Release).
		Str("ingressHost", scenarioCtx.IngressHost).
		Str("keycloakRealm", result.KeycloakRealm).
		Dur("totalDuration", totalDuration).
		Dur("deployDuration", deployDuration).
		Msg("🎉 [executeDeployment] EXIT - scenario deployment completed successfully")

	logging.Logger.Info().
		Str("scenario", scenarioCtx.ScenarioName).
		Str("namespace", scenarioCtx.Namespace).
		Msg("Scenario deployment completed successfully")

	return result
}

// generateTestSecrets creates random secrets for testing.
func generateTestSecrets(envFile string) error {
	text := func() string {
		const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		result := make([]byte, 32)
		for i := range result {
			num, _ := rand.Int(rand.Reader, big.NewInt(int64(len(chars))))
			result[i] = chars[num.Int64()]
		}
		return string(result)
	}

	firstUserPwd := text()
	secondUserPwd := text()
	thirdUserPwd := text()
	keycloakClientsSecret := text()

	if os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD") == "" {
		os.Setenv("DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD", firstUserPwd)
	}
	if os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD") == "" {
		os.Setenv("DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD", secondUserPwd)
	}
	if os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD") == "" {
		os.Setenv("DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD", thirdUserPwd)
	}
	if os.Getenv("DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET") == "" {
		os.Setenv("DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET", keycloakClientsSecret)
	}

	// Persist to .env file
	targetEnvFile := envFile
	if targetEnvFile == "" {
		targetEnvFile = ".env"
	}

	type pair struct{ key, val string }
	toPersist := []pair{
		{"DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD", os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD")},
		{"DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD", os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD")},
		{"DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD", os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD")},
		{"DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET", os.Getenv("DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET")},
	}

	for _, p := range toPersist {
		if err := env.Append(targetEnvFile, p.key, p.val); err != nil {
			logging.Logger.Warn().Err(err).Str("key", p.key).Str("path", targetEnvFile).Msg("Failed to persist generated secret to .env")
		} else {
			logging.Logger.Info().Str("key", p.key).Str("path", targetEnvFile).Msg("Persisted generated secret to .env")
		}
	}

	// Build vault secret mapping
	os.Setenv("vault_secret_mapping", "ci/path DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD;ci/path DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD;ci/path DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD;ci/path DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET;")

	return nil
}

// deleteNamespace deletes a Kubernetes namespace.
func deleteNamespace(ctx context.Context, namespace string) error {
	return kube.DeleteNamespace(ctx, "", "", namespace)
}

// printDeploymentSummary outputs the deployment results.
func printDeploymentSummary(realm, optimizePrefix, orchestrationPrefix string) {
	firstPwd := os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD")
	secondPwd := os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD")
	thirdPwd := os.Getenv("DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD")
	clientSecret := os.Getenv("DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET")

	if !logging.IsTerminal(os.Stdout.Fd()) {
		// Plain, machine-friendly output
		var out strings.Builder
		fmt.Fprintf(&out, "deployment: success\n")
		fmt.Fprintf(&out, "realm: %s\n", realm)
		fmt.Fprintf(&out, "optimizeIndexPrefix: %s\n", optimizePrefix)
		fmt.Fprintf(&out, "orchestrationIndexPrefix: %s\n", orchestrationPrefix)
		fmt.Fprintf(&out, "credentials:\n")
		fmt.Fprintf(&out, "  firstUserPassword: %s\n", firstPwd)
		fmt.Fprintf(&out, "  secondUserPassword: %s\n", secondPwd)
		fmt.Fprintf(&out, "  thirdUserPassword: %s\n", thirdPwd)
		fmt.Fprintf(&out, "  keycloakClientsSecret: %s\n", clientSecret)
		logging.Logger.Info().Msg(out.String())
		return
	}

	// Pretty, human-friendly output
	styleKey := func(s string) string { return logging.Emphasize(s, gchalk.Cyan) }
	styleVal := func(s string) string { return logging.Emphasize(s, gchalk.Magenta) }
	styleOk := func(s string) string { return logging.Emphasize(s, gchalk.Green) }
	styleHead := func(s string) string { return logging.Emphasize(s, gchalk.Bold) }

	var out strings.Builder
	out.WriteString(styleOk("🎉 Deployment completed successfully"))
	out.WriteString("\n\n")

	// Identifiers
	out.WriteString(styleHead("Identifiers"))
	out.WriteString("\n")
	maxKey := 25
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Realm")), styleVal(realm))
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Optimize index prefix")), styleVal(optimizePrefix))
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Orchestration index prefix")), styleVal(orchestrationPrefix))

	out.WriteString("\n")
	out.WriteString(styleHead("Test credentials"))
	out.WriteString("\n")
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "First user password")), styleVal(firstPwd))
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Second user password")), styleVal(secondPwd))
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Third user password")), styleVal(thirdPwd))
	fmt.Fprintf(&out, "  - %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Keycloak clients secret")), styleVal(clientSecret))

	out.WriteString("\n")
	out.WriteString("Please keep these credentials safe. If you have any questions, refer to the documentation or reach out for support. 🚀")

	logging.Logger.Info().Msg(out.String())
}

// printMultiScenarioSummary outputs the deployment results for multiple scenarios.
func printMultiScenarioSummary(results []*ScenarioResult) {
	successCount := 0
	failureCount := 0
	for _, r := range results {
		if r.Error == nil {
			successCount++
		} else {
			failureCount++
		}
	}

	if !logging.IsTerminal(os.Stdout.Fd()) {
		// Plain, machine-friendly output
		var out strings.Builder
		fmt.Fprintf(&out, "parallel deployment: completed\n")
		fmt.Fprintf(&out, "total scenarios: %d\n", len(results))
		fmt.Fprintf(&out, "successful: %d\n", successCount)
		fmt.Fprintf(&out, "failed: %d\n", failureCount)
		fmt.Fprintf(&out, "\nscenarios:\n")
		for _, r := range results {
			fmt.Fprintf(&out, "- scenario: %s\n", r.Scenario)
			fmt.Fprintf(&out, "  namespace: %s\n", r.Namespace)
			fmt.Fprintf(&out, "  release: %s\n", r.Release)
			if r.Error != nil {
				fmt.Fprintf(&out, "  status: failed\n")
				fmt.Fprintf(&out, "  error: %v\n", r.Error)
			} else {
				fmt.Fprintf(&out, "  status: success\n")
				fmt.Fprintf(&out, "  realm: %s\n", r.KeycloakRealm)
				fmt.Fprintf(&out, "  optimizeIndexPrefix: %s\n", r.OptimizeIndexPrefix)
				fmt.Fprintf(&out, "  orchestrationIndexPrefix: %s\n", r.OrchestrationIndexPrefix)
				if r.IngressHost != "" {
					fmt.Fprintf(&out, "  ingressHost: %s\n", r.IngressHost)
				}
				fmt.Fprintf(&out, "  credentials:\n")
				fmt.Fprintf(&out, "    firstUserPassword: %s\n", r.FirstUserPassword)
				fmt.Fprintf(&out, "    secondUserPassword: %s\n", r.SecondUserPassword)
				fmt.Fprintf(&out, "    thirdUserPassword: %s\n", r.ThirdUserPassword)
				fmt.Fprintf(&out, "    keycloakClientsSecret: %s\n", r.KeycloakClientsSecret)
			}
		}
		logging.Logger.Info().Msg(out.String())
		return
	}

	// Pretty, human-friendly output
	styleKey := func(s string) string { return logging.Emphasize(s, gchalk.Cyan) }
	styleVal := func(s string) string { return logging.Emphasize(s, gchalk.Magenta) }
	styleOk := func(s string) string { return logging.Emphasize(s, gchalk.Green) }
	styleErr := func(s string) string { return logging.Emphasize(s, gchalk.Red) }
	styleHead := func(s string) string { return logging.Emphasize(s, gchalk.Bold) }
	styleWarn := func(s string) string { return logging.Emphasize(s, gchalk.Yellow) }

	var out strings.Builder
	if failureCount == 0 {
		out.WriteString(styleOk("🎉 All scenarios deployed successfully!"))
	} else if successCount == 0 {
		out.WriteString(styleErr("❌ All scenarios failed to deploy"))
	} else {
		out.WriteString(styleWarn(fmt.Sprintf("⚠️  Partial success: %d/%d scenarios deployed", successCount, len(results))))
	}
	out.WriteString("\n\n")

	// Summary
	out.WriteString(styleHead("Deployment Summary"))
	out.WriteString("\n")
	fmt.Fprintf(&out, "  Total scenarios: %s\n", styleVal(fmt.Sprintf("%d", len(results))))
	fmt.Fprintf(&out, "  Successful: %s\n", styleOk(fmt.Sprintf("%d", successCount)))
	if failureCount > 0 {
		fmt.Fprintf(&out, "  Failed: %s\n", styleErr(fmt.Sprintf("%d", failureCount)))
	}
	out.WriteString("\n")

	// Details per scenario
	maxKey := 30
	for i, r := range results {
		if i > 0 {
			out.WriteString("\n")
		}

		if r.Error != nil {
			out.WriteString(styleErr(fmt.Sprintf("Scenario %d: %s [FAILED]", i+1, r.Scenario)))
			out.WriteString("\n")
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Namespace")), styleVal(r.Namespace))
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Error")), styleErr(r.Error.Error()))
		} else {
			out.WriteString(styleOk(fmt.Sprintf("Scenario %d: %s [SUCCESS]", i+1, r.Scenario)))
			out.WriteString("\n")
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Namespace")), styleVal(r.Namespace))
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Release")), styleVal(r.Release))
			if r.IngressHost != "" {
				fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Ingress Host")), styleVal(r.IngressHost))
			}
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Keycloak Realm")), styleVal(r.KeycloakRealm))
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Optimize Index Prefix")), styleVal(r.OptimizeIndexPrefix))
			fmt.Fprintf(&out, "  %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey, "Orchestration Index Prefix")), styleVal(r.OrchestrationIndexPrefix))
			out.WriteString(styleHead("  Credentials:"))
			out.WriteString("\n")
			fmt.Fprintf(&out, "    %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey-2, "First user password")), styleVal(r.FirstUserPassword))
			fmt.Fprintf(&out, "    %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey-2, "Second user password")), styleVal(r.SecondUserPassword))
			fmt.Fprintf(&out, "    %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey-2, "Third user password")), styleVal(r.ThirdUserPassword))
			fmt.Fprintf(&out, "    %s: %s\n", styleKey(fmt.Sprintf("%-*s", maxKey-2, "Keycloak clients secret")), styleVal(r.KeycloakClientsSecret))
		}
	}

	out.WriteString("\n")
	if failureCount == 0 {
		out.WriteString("Please keep these credentials safe. All deployments are ready to use! 🚀")
	}

	logging.Logger.Info().Msg(out.String())
}
