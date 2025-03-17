package types

import (
	"os"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(protocol string, port int) error
	ProcessTree(commandPid int) []*os.Process
	VersionCmd(javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd
	CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
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

