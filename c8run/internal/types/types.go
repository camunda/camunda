package types

import (
	"context"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(ctx context.Context, url string) error
	ProcessTree(commandPid int) []int
	VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd
	ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, connectorsVersion string, camundaPort int) *exec.Cmd
	CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
}

type C8RunSettings struct {
	Config               string
	ResolvedConfigPath   string
	ConfigPaths          []string
	Detached             bool
	DisableConnectors    bool
	NoBrowser            bool
	Port                 int
	Keystore             string
	KeystorePassword     string
	LogLevel             string
	SecondaryStorageType string
	Username             string
	Password             string
	StartupUrl           string
	StartupMarkerPath    string
	ExtraDrivers         []string
	// PhysicalTenantsFlag holds the raw --physical-tenants values (comma-separated, repeatable).
	PhysicalTenantsFlag []string
	// PhysicalTenants is the resolved set of extra physical tenants for this run (never "default").
	PhysicalTenants []PhysicalTenant
	// PhysicalTenantsConfigPath is the generated Spring config file declaring the tenants.
	PhysicalTenantsConfigPath string
	// PhysicalTenantsConfig is the generated config content, written when startup proceeds.
	PhysicalTenantsConfig []byte
	// PhysicalTenantsEnv is passed to the Camunda process only (tenant logins, secret paths);
	// it is never exported to c8run's own environment, so child runtimes cannot read it.
	PhysicalTenantsEnv map[string]string
	// OIDC is true when the effective authentication method is OIDC.
	OIDC bool
}

// PhysicalTenant is one extra physical tenant c8run starts next to the implicit "default" one.
type PhysicalTenant struct {
	ID             string
	Username       string
	Password       string
	Connectors     bool
	ConnectorsPort int
}

// HasKeyStore returns true when the keystore and password are set
func (c C8RunSettings) HasKeyStore() bool {
	return c.Keystore != "" && c.KeystorePassword != ""
}

// GetProtocol resolves the protocol to use for accessing Camunda endpoints
func (c C8RunSettings) GetProtocol() string {
	protocol := "http"
	if c.HasKeyStore() {
		protocol = "https"
	}
	return protocol
}

type Processes struct {
	Camunda    Process
	Connectors Process
}

type Process struct {
	Version string
	PidPath string
}

type State struct {
	C8          C8Run
	Settings    C8RunSettings
	ProcessInfo Processes
	// NotReadyTenants lists physical tenants that did not become ready during startup.
	NotReadyTenants []string
}
