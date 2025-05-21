package types

import (
	"context"
	"os"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(ctx context.Context, protocol string, port int) error
	ProcessTree(commandPid int) []*os.Process
	VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string) *exec.Cmd
	CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
}

type C8RunSettings struct {
	Config               string
	Detached             bool
	Port                 int
	Keystore             string
	KeystorePassword     string
	LogLevel             string
	DisableElasticsearch bool
	Username             string
	Password             string
	Docker               bool
}

// HasKeyStore returns true when the keystore and password are set
func (c C8RunSettings) HasKeyStore() bool {
	return c.Keystore != "" && c.KeystorePassword != ""
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
