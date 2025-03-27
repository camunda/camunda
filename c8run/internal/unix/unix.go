//go:build linux || darwin

package unix

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"syscall"
)

func (w *UnixC8Run) OpenBrowser() error {
	operateUrl := "http://localhost:8080/operate/login"
	var openBrowserCmdString string
	if runtime.GOOS == "darwin" {
		openBrowserCmdString = "open"
	} else if runtime.GOOS == "linux" {
		openBrowserCmdString = "xdg-open"
	} else {
		return fmt.Errorf("OpenBrowser: platform %s is not supported", runtime.GOOS)
	}
	openBrowserCmd := exec.Command(openBrowserCmdString, operateUrl)
	err := openBrowserCmd.Run()
	if err != nil {
		return fmt.Errorf("OpenBrowser: failed to open browser %w\n%s", err, debug.Stack())
	}
	return nil
}

func (w *UnixC8Run) ProcessTree(commandPid int) []*os.Process {
	// For unix systems we can kill all processes within a process group by setting a pgid
	// therefore, only the main process needs put in here.
	process := os.Process{Pid: commandPid}
	processes := []*os.Process{&process}
	return processes
}

func (w *UnixC8Run) VersionCmd(javaBinaryPath string) *exec.Cmd {
	return exec.Command(javaBinaryPath, "--version")
}

func (w *UnixC8Run) ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
	elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch")
	elasticsearchCmd := exec.Command(elasticsearchCmdString, "-E", "xpack.ml.enabled=false", "-E", "xpack.security.enabled=false", "-E", "discovery.type=single-node")
	elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return elasticsearchCmd
}

func (w *UnixC8Run) ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	classPath := parentDir + "/*:" + parentDir + "/custom_connectors/*"
	mainClass := "io.camunda.connector.runtime.app.ConnectorRuntimeApplication"
	springConfigLocation := "--spring.config.location=" + parentDir + "/connectors-application.properties"
	connectorsCmd := exec.Command(javaBinary, "-cp", classPath, mainClass, springConfigLocation)
	connectorsCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return connectorsCmd
}

func (w *UnixC8Run) CamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd {
	camundaCmdString := parentDir + "/camunda-zeebe-" + camundaVersion + "/bin/camunda"
	camundaCmd := exec.Command(camundaCmdString, extraArgs)
	camundaCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return camundaCmd
}
