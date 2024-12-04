//go:build !windows

package windows

import (
	"os"
	"os/exec"
)

func (w *WindowsC8Run) OpenBrowser(protocol string, port int) error {
	panic("Platform was not built for windows")
}

func (w *WindowsC8Run) ProcessTree(commandPid int) []*os.Process {
	panic("Platform was not built for windows")
}

func (w *WindowsC8Run) VersionCmd(javaBinaryPath string) *exec.Cmd {
	panic("Platform was not built for windows")
}

func (w *WindowsC8Run) ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
	panic("Platform was not built for windows")
}

func (w *WindowsC8Run) ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	panic("Platform was not built for windows")

}

func (w *WindowsC8Run) CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	panic("Platform was not built for windows")
}
