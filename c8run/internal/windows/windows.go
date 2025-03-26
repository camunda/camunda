//go:build windows

package windows

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime/debug"
	"strconv"
	"syscall"
)

func (w *WindowsC8Run) OpenBrowser(protocol string, port int) error {
	operateUrl := protocol + "://localhost:" + strconv.Itoa(port) + "/operate"
	openBrowserCmdString := "start " + operateUrl
	openBrowserCmd := exec.Command("cmd", "/C", openBrowserCmdString)
	openBrowserCmd.SysProcAttr = &syscall.SysProcAttr{
		// CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	err := openBrowserCmd.Run()
	if err != nil {
		return fmt.Errorf("OpenBrowser: failed to open browser %w\n%s", err, debug.Stack())
	}
	return nil
}

func (w *WindowsC8Run) ProcessTree(commandPid int) []*os.Process {
	return process_tree(int(commandPid))
}

func (w *WindowsC8Run) VersionCmd(javaBinaryPath string) *exec.Cmd {
	return exec.Command("cmd", "/C", javaBinaryPath+" --version")
}

func (w *WindowsC8Run) ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
	elasticsearchCmd := exec.Command(filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch.bat"), "-E", "xpack.ml.enabled=false", "-E", "xpack.security.enabled=false", "-E", "discovery.type=single-node")

	elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	return elasticsearchCmd
}

func (w *WindowsC8Run) ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	connectorsCmd := exec.Command(javaBinary, "-classpath", parentDir+"\\*;"+parentDir+"\\custom_connectors\\*", "io.camunda.connector.runtime.app.ConnectorRuntimeApplication", "--spring.config.location="+parentDir+"\\connectors-application.properties")
	connectorsCmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	return connectorsCmd
}

func (w *WindowsC8Run) CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	camundaCmd := exec.Command(".\\camunda-zeebe-"+camundaVersion+"\\bin\\camunda.bat", extraArgs)
	if javaOpts != "" {
		camundaCmd.Env = append(os.Environ(), "JAVA_OPTS="+javaOpts)
	}
	camundaCmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	return camundaCmd
}
