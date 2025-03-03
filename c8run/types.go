package main

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
	config               string
	detached             bool
	port                 int
	keystore             string
	keystorePassword     string
	logLevel             string
	disableElasticsearch bool
	docker               bool
}

type TemplateData struct {
	ServerPort int
}

// HasKeyStore returns true when the keystore and password are set
func (c C8RunSettings) HasKeyStore() bool {
	return c.keystore != "" && c.keystorePassword != ""
}

func (c C8RunSettings) Port() int {
	return c.port
}
