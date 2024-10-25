package main

import (
	"os"
	"os/exec"
)

type C8Run interface {
	OpenBrowser(name string)
	GetProcessTree(commandPid int) []*os.Process
	GetVersionCmd(javaBinaryPath string) *exec.Cmd
	GetElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd
	GetConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd
	GetCamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd
}

type C8RunSettings struct {
	config   string
	detached bool
}
