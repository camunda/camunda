package types

import "time"

type ValuesInput struct {
	ChartPath string
	Scenarios []string
	Auth      string
}

type Options struct {
	Chart       string
	ChartPath   string
	Version     string
	RealmPath   string
	ReleaseName string
	Namespace   string
	Kubeconfig  string
	KubeContext string

	Timeout     time.Duration
	Wait        bool
	Atomic      bool
	IngressHost string

	ValuesFiles []string
	SetPairs    map[string]string
	ExtraArgs   []string

	Identifier string
	TTL        string

	// CI metadata (optional)
	CIMetadata CIMetadata

	// Registry/cluster behaviors
	EnsureDockerRegistry   bool
	DockerRegistryUsername string
	DockerRegistryPassword string
	SkipDockerLogin        bool
	SkipDependencyUpdate   bool
	ApplyIntegrationCreds  bool

	PostRendererPath string

	ExternalSecretsEnabled bool
	Platform               string // gke|rosa|eks
	NamespacePrefix        string // for eks copy
	RepoRoot               string // repo base for manifests

	TLSSecretName string
	TLSCertPath   string
	TLSKeyPath    string

	LoadKeycloakRealm bool
	KeycloakRealmName string
	VaultSecretPath   string

	// Template rendering (no cluster changes)
	RenderTemplates bool
	RenderOutputDir string
	IncludeCRDs     bool
}

type CIMetadata struct {
	GithubRunID string
	GithubJobID string
	GithubOrg   string
	GithubRepo  string
	WorkflowURL string
	Flow        string
}
