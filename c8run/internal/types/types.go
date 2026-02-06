package types

import (
	"context"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(ctx context.Context, url string) error
	ProcessTree(commandPid int) []int
	VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, connectorsVersion string, camundaPort int) *exec.Cmd
	CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
}

type C8RunSettings struct {
	Config               string
	ResolvedConfigPath   string
	Detached             bool
	Port                 int
	Keystore             string
	KeystorePassword     string
	LogLevel             string
	DisableElasticsearch bool
	SecondaryStorageType string
	Username             string
	Password             string
	Docker               bool
	StartupUrl           string
	ExtraDrivers         []string
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
	Camunda       Process
	Connectors    Process
	Elasticsearch Process
}

type Process struct {
	Version string
	PidPath string
}

type State struct {
	C8          C8Run
	Settings    C8RunSettings
	ProcessInfo Processes
}
