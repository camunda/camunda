package config

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

const (
	// DefaultIngressBaseDomain is the base domain for CI ingress hosts.
	DefaultIngressBaseDomain = "ci.distro.ultrawombat.com"
)

// KeycloakConfig holds Keycloak connection settings.
type KeycloakConfig struct {
	Host     string `mapstructure:"host" yaml:"host,omitempty"`
	Protocol string `mapstructure:"protocol" yaml:"protocol,omitempty"`
}

// DeploymentConfig represents a single deployment profile.
type DeploymentConfig struct {
	Name                     string   `mapstructure:"-" yaml:"-"` // filled at runtime from map key
	Chart                    string   `mapstructure:"chart" yaml:"chart,omitempty"`
	Version                  string   `mapstructure:"version" yaml:"version,omitempty"`
	Scenario                 string   `mapstructure:"scenario" yaml:"scenario,omitempty"`
	ChartPath                string   `mapstructure:"chartPath" yaml:"chartPath,omitempty"`
	Namespace                string   `mapstructure:"namespace" yaml:"namespace,omitempty"`
	Release                  string   `mapstructure:"release" yaml:"release,omitempty"`
	ScenarioPath             string   `mapstructure:"scenarioPath" yaml:"scenarioPath,omitempty"`
	Auth                     string   `mapstructure:"auth" yaml:"auth,omitempty"`
	Platform                 string   `mapstructure:"platform" yaml:"platform,omitempty"`
	LogLevel                 string   `mapstructure:"logLevel" yaml:"logLevel,omitempty"`
	ExternalSecrets          *bool    `mapstructure:"externalSecrets" yaml:"externalSecrets,omitempty"`
	SkipDependencyUpdate     *bool    `mapstructure:"skipDependencyUpdate" yaml:"skipDependencyUpdate,omitempty"`
	KeycloakRealm            string   `mapstructure:"keycloakRealm" yaml:"keycloakRealm,omitempty"`
	OptimizeIndexPrefix      string   `mapstructure:"optimizeIndexPrefix" yaml:"optimizeIndexPrefix,omitempty"`
	OrchestrationIndexPrefix string   `mapstructure:"orchestrationIndexPrefix" yaml:"orchestrationIndexPrefix,omitempty"`
	TasklistIndexPrefix      string   `mapstructure:"tasklistIndexPrefix" yaml:"tasklistIndexPrefix,omitempty"`
	OperateIndexPrefix       string   `mapstructure:"operateIndexPrefix" yaml:"operateIndexPrefix,omitempty"`
	IngressHost              string   `mapstructure:"ingressHost" yaml:"ingressHost,omitempty"`
	Flow                     string   `mapstructure:"flow" yaml:"flow,omitempty"`
	EnvFile                  string   `mapstructure:"envFile" yaml:"envFile,omitempty"`
	Interactive              *bool    `mapstructure:"interactive" yaml:"interactive,omitempty"`
	VaultSecretMapping       string   `mapstructure:"vaultSecretMapping" yaml:"vaultSecretMapping,omitempty"`
	AutoGenerateSecrets      *bool    `mapstructure:"autoGenerateSecrets" yaml:"autoGenerateSecrets,omitempty"`
	DeleteNamespace          *bool    `mapstructure:"deleteNamespace" yaml:"deleteNamespace,omitempty"`
	DockerUsername           string   `mapstructure:"dockerUsername" yaml:"dockerUsername,omitempty"`
	DockerPassword           string   `mapstructure:"dockerPassword" yaml:"dockerPassword,omitempty"`
	EnsureDockerRegistry     *bool    `mapstructure:"ensureDockerRegistry" yaml:"ensureDockerRegistry,omitempty"`
	RenderTemplates          *bool    `mapstructure:"renderTemplates" yaml:"renderTemplates,omitempty"`
	RenderOutputDir          string   `mapstructure:"renderOutputDir" yaml:"renderOutputDir,omitempty"`
	ExtraValues              []string `mapstructure:"extraValues" yaml:"extraValues,omitempty"`
	RepoRoot                 string   `mapstructure:"repoRoot" yaml:"repoRoot,omitempty"`
	ScenarioRoot             string   `mapstructure:"scenarioRoot" yaml:"scenarioRoot,omitempty"`
	ValuesPreset             string   `mapstructure:"valuesPreset" yaml:"valuesPreset,omitempty"`
}

// RootConfig represents the entire configuration file.
type RootConfig struct {
	Current                  string                      `mapstructure:"current" yaml:"current,omitempty"`
	RepoRoot                 string                      `mapstructure:"repoRoot" yaml:"repoRoot,omitempty"`
	ScenarioRoot             string                      `mapstructure:"scenarioRoot" yaml:"scenarioRoot,omitempty"`
	ValuesPreset             string                      `mapstructure:"valuesPreset" yaml:"valuesPreset,omitempty"`
	ChartPath                string                      `mapstructure:"chartPath" yaml:"chartPath,omitempty"`
	Chart                    string                      `mapstructure:"chart" yaml:"chart,omitempty"`
	Version                  string                      `mapstructure:"version" yaml:"version,omitempty"`
	Namespace                string                      `mapstructure:"namespace" yaml:"namespace,omitempty"`
	Release                  string                      `mapstructure:"release" yaml:"release,omitempty"`
	Scenario                 string                      `mapstructure:"scenario" yaml:"scenario,omitempty"`
	ScenarioPath             string                      `mapstructure:"scenarioPath" yaml:"scenarioPath,omitempty"`
	Auth                     string                      `mapstructure:"auth" yaml:"auth,omitempty"`
	Platform                 string                      `mapstructure:"platform" yaml:"platform,omitempty"`
	LogLevel                 string                      `mapstructure:"logLevel" yaml:"logLevel,omitempty"`
	ExternalSecrets          bool                        `mapstructure:"externalSecrets" yaml:"externalSecrets,omitempty"`
	SkipDependencyUpdate     bool                        `mapstructure:"skipDependencyUpdate" yaml:"skipDependencyUpdate,omitempty"`
	KeycloakRealm            string                      `mapstructure:"keycloakRealm" yaml:"keycloakRealm,omitempty"`
	OptimizeIndexPrefix      string                      `mapstructure:"optimizeIndexPrefix" yaml:"optimizeIndexPrefix,omitempty"`
	OrchestrationIndexPrefix string                      `mapstructure:"orchestrationIndexPrefix" yaml:"orchestrationIndexPrefix,omitempty"`
	TasklistIndexPrefix      string                      `mapstructure:"tasklistIndexPrefix" yaml:"tasklistIndexPrefix,omitempty"`
	OperateIndexPrefix       string                      `mapstructure:"operateIndexPrefix" yaml:"operateIndexPrefix,omitempty"`
	IngressHost              string                      `mapstructure:"ingressHost" yaml:"ingressHost,omitempty"`
	Flow                     string                      `mapstructure:"flow" yaml:"flow,omitempty"`
	EnvFile                  string                      `mapstructure:"envFile" yaml:"envFile,omitempty"`
	Interactive              *bool                       `mapstructure:"interactive" yaml:"interactive,omitempty"`
	VaultSecretMapping       string                      `mapstructure:"vaultSecretMapping" yaml:"vaultSecretMapping,omitempty"`
	AutoGenerateSecrets      *bool                       `mapstructure:"autoGenerateSecrets" yaml:"autoGenerateSecrets,omitempty"`
	DeleteNamespaceFirst     *bool                       `mapstructure:"deleteNamespace" yaml:"deleteNamespace,omitempty"`
	DockerUsername           string                      `mapstructure:"dockerUsername" yaml:"dockerUsername,omitempty"`
	DockerPassword           string                      `mapstructure:"dockerPassword" yaml:"dockerPassword,omitempty"`
	EnsureDockerRegistry     *bool                       `mapstructure:"ensureDockerRegistry" yaml:"ensureDockerRegistry,omitempty"`
	RenderTemplates          *bool                       `mapstructure:"renderTemplates" yaml:"renderTemplates,omitempty"`
	RenderOutputDir          string                      `mapstructure:"renderOutputDir" yaml:"renderOutputDir,omitempty"`
	ExtraValues              []string                    `mapstructure:"extraValues" yaml:"extraValues,omitempty"`
	Keycloak                 KeycloakConfig              `mapstructure:"keycloak" yaml:"keycloak,omitempty"`
	Deployments              map[string]DeploymentConfig `mapstructure:"deployments" yaml:"deployments,omitempty"`
	FilePath                 string                      `mapstructure:"-" yaml:"-"`
}

// ResolvePath determines the config file path to use.
func ResolvePath(explicit string) (string, error) {
	if strings.TrimSpace(explicit) != "" {
		return explicit, nil
	}
	// prefer local project file if present
	local := ".camunda-deploy.yaml"
	if _, err := os.Stat(local); err == nil {
		return local, nil
	}
	// fallback to user config directory
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".config", "camunda", "deploy.yaml"), nil
}

// Read loads configuration from the specified path.
func Read(path string, includeEnv bool) (*RootConfig, error) {
	rc := &RootConfig{}
	// Missing file is not an error; we will create it on write
	if data, err := os.ReadFile(path); err == nil {
		if err := yaml.Unmarshal(data, rc); err != nil {
			return nil, fmt.Errorf("failed to parse config %q: %w", path, err)
		}
	}
	// Apply environment overrides (CAMUNDA_*) only when requested
	if includeEnv {
		applyEnvOverrides(rc)
	}
	rc.FilePath = path
	return rc, nil
}

// Write saves the configuration to disk.
func Write(rc *RootConfig) error {
	if strings.TrimSpace(rc.FilePath) == "" {
		return fmt.Errorf("no config file path resolved for writing")
	}
	dir := filepath.Dir(rc.FilePath)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	out, err := yaml.Marshal(rc)
	if err != nil {
		return err
	}
	return os.WriteFile(rc.FilePath, out, fs.FileMode(0o644))
}

// WriteCurrentOnly updates only the top-level "current" key in the YAML file.
func WriteCurrentOnly(path string, current string) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	content, err := os.ReadFile(path)
	if err != nil {
		// If file doesn't exist, create a minimal one
		minimal := map[string]any{"current": current}
		out, mErr := yaml.Marshal(minimal)
		if mErr != nil {
			return mErr
		}
		return os.WriteFile(path, out, fs.FileMode(0o644))
	}
	var data map[string]any
	if err := yaml.Unmarshal(content, &data); err != nil || data == nil {
		data = map[string]any{}
	}
	data["current"] = current
	out, err := yaml.Marshal(data)
	if err != nil {
		return err
	}
	return os.WriteFile(path, out, fs.FileMode(0o644))
}

// applyEnvOverrides applies environment variables to the config.
func applyEnvOverrides(rc *RootConfig) {
	if rc == nil {
		return
	}
	get := func(key string) string { return strings.TrimSpace(os.Getenv(key)) }
	if v := get("CAMUNDA_CURRENT"); v != "" {
		rc.Current = v
	}
	if v := get("CAMUNDA_REPO_ROOT"); v != "" {
		rc.RepoRoot = v
	}
	if v := get("CAMUNDA_SCENARIO_ROOT"); v != "" {
		rc.ScenarioRoot = v
	}
	if v := get("CAMUNDA_VALUES_PRESET"); v != "" {
		rc.ValuesPreset = v
	}
	if v := get("CAMUNDA_PLATFORM"); v != "" {
		rc.Platform = v
	}
	if v := get("CAMUNDA_LOG_LEVEL"); v != "" {
		rc.LogLevel = v
	}
	if v := get("CAMUNDA_EXTERNAL_SECRETS"); v != "" {
		rc.ExternalSecrets = strings.EqualFold(v, "true") || v == "1"
	}
	if v := get("CAMUNDA_SKIP_DEPENDENCY_UPDATE"); v != "" {
		rc.SkipDependencyUpdate = strings.EqualFold(v, "true") || v == "1"
	}
	if v := get("CAMUNDA_KEYCLOAK_HOST"); v != "" {
		rc.Keycloak.Host = v
	}
	if v := get("CAMUNDA_KEYCLOAK_PROTOCOL"); v != "" {
		rc.Keycloak.Protocol = v
	}
	if v := get("CAMUNDA_KEYCLOAK_REALM"); v != "" {
		rc.KeycloakRealm = v
	}
	if v := get("CAMUNDA_OPTIMIZE_INDEX_PREFIX"); v != "" {
		rc.OptimizeIndexPrefix = v
	}
	if v := get("CAMUNDA_ORCHESTRATION_INDEX_PREFIX"); v != "" {
		rc.OrchestrationIndexPrefix = v
	}
	if v := get("CAMUNDA_TASKLIST_INDEX_PREFIX"); v != "" {
		rc.TasklistIndexPrefix = v
	}
	if v := get("CAMUNDA_OPERATE_INDEX_PREFIX"); v != "" {
		rc.OperateIndexPrefix = v
	}
	if v := get("CAMUNDA_HOSTNAME"); v != "" {
		rc.IngressHost = v
	}
}

// firstNonEmpty returns the first non-empty string.
func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return ""
}

// MergeStringField applies deployment/root value to target if target is empty.
func MergeStringField(target *string, depVal, rootVal string) {
	if strings.TrimSpace(*target) == "" {
		*target = firstNonEmpty(depVal, rootVal)
	}
}

// MergeBoolField applies deployment/root value to target if target pointer is nil.
func MergeBoolField(target *bool, depVal, rootVal *bool) {
	if depVal != nil {
		*target = *depVal
	} else if rootVal != nil {
		*target = *rootVal
	}
}

// MergeStringSliceField applies deployment/root value to target if target is empty.
func MergeStringSliceField(target *[]string, depVal, rootVal []string) {
	if len(*target) == 0 {
		if len(depVal) > 0 {
			*target = append(*target, depVal...)
		} else if len(rootVal) > 0 {
			*target = append(*target, rootVal...)
		}
	}
}
