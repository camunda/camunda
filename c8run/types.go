package main

import (
	"os"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(protocol string) error
	ProcessTree(commandPid int) []*os.Process
	VersionCmd(javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd
	CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
}

type C8RunSettings struct {
	config           string
	detached         bool
	keystore         string
	keystorePassword string
}
