//go:build !(linux || darwin)

package unix

import (
	"os/exec"

	"github.com/camunda/camunda/c8run/internal/types"
)

func (w *UnixC8Run) OpenBrowser(protocol string, port int) error {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ProcessTree(commandPid int) []types.C8RunProcess {
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
