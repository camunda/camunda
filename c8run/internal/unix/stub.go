//go:build !(linux || darwin)

package unix

import (
	"os"
	"os/exec"
)

func (w *UnixC8Run) OpenBrowser(protocol string) error {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ProcessTree(commandPid int) []*os.Process {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) VersionCmd(javaBinaryPath string) *exec.Cmd {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	panic("Platform was not built for unix")

}

func (w *UnixC8Run) CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	panic("Platform was not built for unix")
}
