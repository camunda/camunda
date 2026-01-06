//go:build !(linux || darwin)

package unix

import (
	"context"
	"os"
	"os/exec"
)

func (w *UnixC8Run) OpenBrowser(ctx context.Context, url string) error {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ProcessTree(commandPid int) []*os.Process {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string, camundaPort int) *exec.Cmd {
	panic("Platform was not built for unix")
}

func (w *UnixC8Run) CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	panic("Platform was not built for unix")
}
