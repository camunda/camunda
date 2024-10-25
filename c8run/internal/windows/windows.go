//go:build windows

package windows

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
)


func (w *WindowsC8Run) OpenBrowser(name string) {
	operateUrl := "http://localhost:8080/operate/login"
	openBrowserCmdString := "start " + operateUrl
	openBrowserCmd := exec.Command("cmd", "/C", openBrowserCmdString)
	openBrowserCmd.SysProcAttr = &syscall.SysProcAttr{
		// CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	fmt.Println(name + " has successfully been started.")
	openBrowserCmd.Run()
}

func (w *WindowsC8Run) GetProcessTree(commandPid int) []*os.Process{
        return process_tree(int(commandPid))
}

func (w *WindowsC8Run) GetVersionCmd(javaBinaryPath string) *exec.Cmd {
        return exec.Command("cmd", "/C", javaBinaryPath + " --version")
}

func (w *WindowsC8Run) GetElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
        elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch.bat") + " -E xpack.ml.enabled=false -E xpack.security.enabled=false"
        elasticsearchCmd := exec.Command("cmd", "/C", elasticsearchCmdString)

        elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{
                CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
        }
        return elasticsearchCmd
}

func (w *WindowsC8Run) GetConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
        connectorsCmdString := javaBinary + " -classpath " + parentDir + "\\*;" + parentDir + "\\custom_connectors\\*;" + parentDir + "\\camunda-zeebe-" + camundaVersion + "\\lib\\* io.camunda.connector.runtime.app.ConnectorRuntimeApplication --spring.config.location=" + parentDir + "\\connectors-application.properties"
        connectorsCmd := exec.Command("cmd", "/C", connectorsCmdString)
        connectorsCmd.SysProcAttr = &syscall.SysProcAttr{
                CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
        }
        return connectorsCmd

}

func (w *WindowsC8Run) GetCamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd {
        camundaCmdString := parentDir + "\\camunda-zeebe-" + camundaVersion + "\\bin\\camunda " + extraArgs
        fmt.Println(camundaCmdString)
        camundaCmd := exec.Command("cmd", "/C", camundaCmdString)
        camundaCmd.SysProcAttr = &syscall.SysProcAttr{
                CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
                // CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
        }
        return camundaCmd
}

