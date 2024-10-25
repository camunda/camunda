//go:build !( linux || darwin )

package unix

import (
	"os"
	"os/exec"
)

func (w *UnixC8Run) OpenBrowser(name string) {
        panic("Platform was not built for unix")
}

func (w *UnixC8Run) GetProcessTree(commandPid int) []*os.Process{
        panic("Platform was not built for unix")
}

func (w *UnixC8Run) GetVersionCmd(javaBinaryPath string) *exec.Cmd {
        panic("Platform was not built for unix")
}

func (w *UnixC8Run) GetElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
        panic("Platform was not built for unix")
}

func (w *UnixC8Run) GetConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
        panic("Platform was not built for unix")

}

func (w *UnixC8Run) GetCamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd {
        panic("Platform was not built for unix")
}

