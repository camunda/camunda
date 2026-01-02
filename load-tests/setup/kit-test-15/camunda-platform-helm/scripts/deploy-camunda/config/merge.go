package config

import (
	"fmt"
	"strings"
)

// RuntimeFlags holds all CLI flag values that can be merged with config.
type RuntimeFlags struct {
	ChartPath                string
	Chart                    string
	ChartVersion             string
	Namespace                string
	Release                  string
	Scenario                 string   // Single scenario or comma-separated list
	Scenarios                []string // Parsed list of scenarios (populated by Validate)
	ScenarioPath             string
	Auth                     string
	Platform                 string
	LogLevel                 string
	SkipDependencyUpdate     bool
	ExternalSecrets          bool
	KeycloakHost             string
	KeycloakProtocol         string
	KeycloakRealm            string
	OptimizeIndexPrefix      string
	OrchestrationIndexPrefix string
	TasklistIndexPrefix      string
	OperateIndexPrefix       string
	IngressSubdomain         string
	IngressHostname          string
	RepoRoot                 string
	Flow                     string
	EnvFile                  string
	Interactive              bool
	VaultSecretMapping       string
	AutoGenerateSecrets      bool
	DeleteNamespaceFirst     bool
	DockerUsername           string
	DockerPassword           string
	EnsureDockerRegistry     bool
	RenderTemplates          bool
	RenderOutputDir          string
	ExtraValues              []string
	ValuesPreset             string
	Timeout                  int // Timeout in minutes for Helm deployment
}

// ApplyActiveDeployment merges active deployment and root config into runtime flags.
func ApplyActiveDeployment(rc *RootConfig, active string, flags *RuntimeFlags) error {
	if rc == nil || rc.Deployments == nil {
		return applyRootDefaults(rc, flags)
	}

	// Auto-select if exactly one deployment exists
	if strings.TrimSpace(active) == "" && len(rc.Deployments) == 1 {
		for name := range rc.Deployments {
			active = name
		}
	}

	if strings.TrimSpace(active) == "" {
		return applyRootDefaults(rc, flags)
	}

	dep, ok := rc.Deployments[active]
	if !ok {
		return fmt.Errorf("active deployment %q not found in config", active)
	}

	// Apply deployment-specific values
	MergeStringField(&flags.ChartPath, dep.ChartPath, rc.ChartPath)
	MergeStringField(&flags.Chart, dep.Chart, rc.Chart)
	MergeStringField(&flags.ChartVersion, dep.Version, rc.Version)
	MergeStringField(&flags.Namespace, dep.Namespace, rc.Namespace)
	MergeStringField(&flags.Release, dep.Release, rc.Release)
	MergeStringField(&flags.Scenario, dep.Scenario, rc.Scenario)
	MergeStringField(&flags.Auth, dep.Auth, rc.Auth)
	MergeStringField(&flags.Platform, dep.Platform, rc.Platform)
	MergeStringField(&flags.LogLevel, dep.LogLevel, rc.LogLevel)
	MergeStringField(&flags.Flow, dep.Flow, rc.Flow)
	MergeStringField(&flags.EnvFile, dep.EnvFile, rc.EnvFile)
	MergeStringField(&flags.VaultSecretMapping, dep.VaultSecretMapping, rc.VaultSecretMapping)
	MergeStringField(&flags.DockerUsername, dep.DockerUsername, rc.DockerUsername)
	MergeStringField(&flags.DockerPassword, dep.DockerPassword, rc.DockerPassword)
	MergeStringField(&flags.RenderOutputDir, dep.RenderOutputDir, rc.RenderOutputDir)
	MergeStringField(&flags.RepoRoot, dep.RepoRoot, rc.RepoRoot)
	MergeStringField(&flags.ValuesPreset, dep.ValuesPreset, rc.ValuesPreset)
	MergeStringField(&flags.KeycloakRealm, dep.KeycloakRealm, rc.KeycloakRealm)
	MergeStringField(&flags.OptimizeIndexPrefix, dep.OptimizeIndexPrefix, rc.OptimizeIndexPrefix)
	MergeStringField(&flags.OrchestrationIndexPrefix, dep.OrchestrationIndexPrefix, rc.OrchestrationIndexPrefix)
	MergeStringField(&flags.TasklistIndexPrefix, dep.TasklistIndexPrefix, rc.TasklistIndexPrefix)
	MergeStringField(&flags.OperateIndexPrefix, dep.OperateIndexPrefix, rc.OperateIndexPrefix)

	// ScenarioPath special handling
	if strings.TrimSpace(flags.ScenarioPath) == "" {
		flags.ScenarioPath = firstNonEmpty(dep.ScenarioPath, dep.ScenarioRoot, rc.ScenarioPath, rc.ScenarioRoot)
	}

	// Boolean fields - apply if flag wasn't explicitly set
	MergeBoolField(&flags.ExternalSecrets, dep.ExternalSecrets, boolPtr(rc.ExternalSecrets))
	MergeBoolField(&flags.SkipDependencyUpdate, dep.SkipDependencyUpdate, boolPtr(rc.SkipDependencyUpdate))
	MergeBoolField(&flags.Interactive, dep.Interactive, rc.Interactive)
	MergeBoolField(&flags.AutoGenerateSecrets, dep.AutoGenerateSecrets, rc.AutoGenerateSecrets)
	MergeBoolField(&flags.DeleteNamespaceFirst, dep.DeleteNamespace, rc.DeleteNamespaceFirst)
	MergeBoolField(&flags.EnsureDockerRegistry, dep.EnsureDockerRegistry, rc.EnsureDockerRegistry)
	MergeBoolField(&flags.RenderTemplates, dep.RenderTemplates, rc.RenderTemplates)

	// Slice fields
	MergeStringSliceField(&flags.ExtraValues, dep.ExtraValues, rc.ExtraValues)

	// Keycloak
	MergeStringField(&flags.KeycloakHost, "", rc.Keycloak.Host)
	MergeStringField(&flags.KeycloakProtocol, "", rc.Keycloak.Protocol)

	return nil
}

// applyRootDefaults applies only root-level defaults when no deployment is active.
func applyRootDefaults(rc *RootConfig, flags *RuntimeFlags) error {
	if rc == nil {
		return nil
	}

	MergeStringField(&flags.ChartPath, "", rc.ChartPath)
	MergeStringField(&flags.Chart, "", rc.Chart)
	MergeStringField(&flags.ChartVersion, "", rc.Version)
	MergeStringField(&flags.Namespace, "", rc.Namespace)
	MergeStringField(&flags.Release, "", rc.Release)
	MergeStringField(&flags.Scenario, "", rc.Scenario)
	MergeStringField(&flags.ScenarioPath, "", firstNonEmpty(rc.ScenarioPath, rc.ScenarioRoot))
	MergeStringField(&flags.Auth, "", rc.Auth)
	MergeStringField(&flags.Platform, "", rc.Platform)
	MergeStringField(&flags.LogLevel, "", rc.LogLevel)
	MergeStringField(&flags.Flow, "", rc.Flow)
	MergeStringField(&flags.EnvFile, "", rc.EnvFile)
	MergeStringField(&flags.VaultSecretMapping, "", rc.VaultSecretMapping)
	MergeStringField(&flags.DockerUsername, "", rc.DockerUsername)
	MergeStringField(&flags.DockerPassword, "", rc.DockerPassword)
	MergeStringField(&flags.RenderOutputDir, "", rc.RenderOutputDir)
	MergeStringField(&flags.RepoRoot, "", rc.RepoRoot)
	MergeStringField(&flags.ValuesPreset, "", rc.ValuesPreset)
	MergeStringField(&flags.KeycloakRealm, "", rc.KeycloakRealm)
	MergeStringField(&flags.OptimizeIndexPrefix, "", rc.OptimizeIndexPrefix)
	MergeStringField(&flags.OrchestrationIndexPrefix, "", rc.OrchestrationIndexPrefix)
	MergeStringField(&flags.TasklistIndexPrefix, "", rc.TasklistIndexPrefix)
	MergeStringField(&flags.OperateIndexPrefix, "", rc.OperateIndexPrefix)

	if rc.ExternalSecrets {
		flags.ExternalSecrets = true
	}
	if rc.SkipDependencyUpdate {
		flags.SkipDependencyUpdate = true
	}

	MergeBoolField(&flags.Interactive, nil, rc.Interactive)
	MergeBoolField(&flags.AutoGenerateSecrets, nil, rc.AutoGenerateSecrets)
	MergeBoolField(&flags.DeleteNamespaceFirst, nil, rc.DeleteNamespaceFirst)
	MergeBoolField(&flags.EnsureDockerRegistry, nil, rc.EnsureDockerRegistry)
	MergeBoolField(&flags.RenderTemplates, nil, rc.RenderTemplates)

	MergeStringSliceField(&flags.ExtraValues, nil, rc.ExtraValues)

	MergeStringField(&flags.KeycloakHost, "", rc.Keycloak.Host)
	MergeStringField(&flags.KeycloakProtocol, "", rc.Keycloak.Protocol)

	return nil
}

// boolPtr returns a pointer to a bool value.
func boolPtr(b bool) *bool {
	return &b
}

// Validate performs validation on the merged runtime flags.
func Validate(flags *RuntimeFlags) error {
	// Ensure at least one of chart-path or chart is provided
	if flags.ChartPath == "" && flags.Chart == "" {
		return fmt.Errorf("either --chart-path or --chart must be provided")
	}

	// Validate --version compatibility
	if strings.TrimSpace(flags.ChartVersion) != "" && strings.TrimSpace(flags.Chart) == "" && strings.TrimSpace(flags.ChartPath) != "" {
		return fmt.Errorf("--version requires --chart to be set; do not combine --version with only --chart-path")
	}
	if strings.TrimSpace(flags.ChartVersion) != "" && strings.TrimSpace(flags.Chart) == "" && strings.TrimSpace(flags.ChartPath) == "" {
		return fmt.Errorf("--version requires --chart to be set")
	}

	// Validate required runtime identifiers
	if strings.TrimSpace(flags.Namespace) == "" {
		return fmt.Errorf("namespace not set; provide -n/--namespace or set 'namespace' in the active deployment/root config")
	}
	if strings.TrimSpace(flags.Release) == "" {
		return fmt.Errorf("release not set; provide -r/--release or set 'release' in the active deployment/root config")
	}
	if strings.TrimSpace(flags.Scenario) == "" {
		return fmt.Errorf("scenario not set; provide -s/--scenario or set 'scenario' in the active deployment/root config")
	}

	// Parse scenarios from comma-separated string
	flags.Scenarios = parseScenarios(flags.Scenario)
	if len(flags.Scenarios) == 0 {
		return fmt.Errorf("no valid scenarios found in %q", flags.Scenario)
	}

	return nil
}

// parseScenarios splits a comma-separated scenario string into a slice.
func parseScenarios(scenario string) []string {
	var scenarios []string
	for _, s := range strings.Split(scenario, ",") {
		s = strings.TrimSpace(s)
		if s != "" {
			scenarios = append(scenarios, s)
		}
	}
	return scenarios
}

// ResolveIngressHostname returns the resolved ingress hostname.
// If IngressHostname is set, it takes precedence (full override).
// Otherwise, IngressSubdomain is appended to DefaultIngressBaseDomain.
func (f *RuntimeFlags) ResolveIngressHostname() string {
	if f.IngressHostname != "" {
		return f.IngressHostname
	}
	if f.IngressSubdomain != "" {
		return f.IngressSubdomain + "." + DefaultIngressBaseDomain
	}
	return ""
// LoadAndMerge loads config from the given path and merges the active deployment into flags.
// If configPath is empty, it resolves the default config location.
// The includeEnv parameter controls whether environment variable overrides are applied.
func LoadAndMerge(configPath string, includeEnv bool, flags *RuntimeFlags) (*RootConfig, error) {
	cfgPath, err := ResolvePath(configPath)
	if err != nil {
		return nil, err
	}
	rc, err := Read(cfgPath, includeEnv)
	if err != nil {
		return nil, err
	}
	if err := ApplyActiveDeployment(rc, rc.Current, flags); err != nil {
		return nil, err
	}
	return rc, nil
}
