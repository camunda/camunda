//go:build !windows

package windows

import (
	"os"
	"os/exec"
)

func (w *WindowsC8Run) OpenBrowser(name string) {
        panic("Platform was not built for windows")
}

func (w *WindowsC8Run) GetProcessTree(commandPid int) []*os.Process{
        panic("Platform was not built for windows")
}

func (w *WindowsC8Run) GetVersionCmd(javaBinaryPath string) *exec.Cmd {
        panic("Platform was not built for windows")
}

func (w *WindowsC8Run) GetElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
        panic("Platform was not built for windows")
}

func (w *WindowsC8Run) GetConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
        panic("Platform was not built for windows")

}

func (w *WindowsC8Run) GetCamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd {
        panic("Platform was not built for windows")
}

