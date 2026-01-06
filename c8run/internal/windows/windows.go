//go:build windows

package windows

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime/debug"
	"syscall"
)

func (w *WindowsC8Run) OpenBrowser(ctx context.Context, url string) error {
	openBrowserCmdString := "start " + url
	openBrowserCmd := exec.CommandContext(ctx, "cmd", "/C", openBrowserCmdString)
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

func (w *WindowsC8Run) VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd {
	return exec.CommandContext(ctx, "cmd", "/C", javaBinaryPath+" --version")
}

func (w *WindowsC8Run) ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd {
	elasticsearchCmd := exec.CommandContext(ctx, filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch.bat"), "-E", "xpack.ml.enabled=false", "-E", "xpack.security.enabled=false", "-E", "discovery.type=single-node")

	elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}
	return elasticsearchCmd
}

func (w *WindowsC8Run) ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string, camundaPort int) *exec.Cmd {
	connectorsCmd := exec.CommandContext(ctx, javaBinary, "-classpath", parentDir+"\\*;"+parentDir+"\\custom_connectors\\*", "io.camunda.connector.runtime.app.ConnectorRuntimeApplication", "--spring.config.additional-location="+parentDir+"\\connectors-application.properties")
	connectorsCmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: 0x08000000 | 0x00000200, // CREATE_NO_WINDOW, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000008 | 0x00000200, // DETACHED_PROCESS, CREATE_NEW_PROCESS_GROUP : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
		// CreationFlags: 0x00000010, // CREATE_NEW_CONSOLE : https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags
	}

	// Set default Zeebe REST address if the user has not provided one already.
	if _, exists := os.LookupEnv("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS"); !exists {
		zeebeRestAddress := fmt.Sprintf("http://localhost:%d", camundaPort)
		connectorsCmd.Env = append(os.Environ(), fmt.Sprintf("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=%s", zeebeRestAddress))
	}

	return connectorsCmd
}

func (w *WindowsC8Run) CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	camundaCmd := exec.CommandContext(ctx, ".\\camunda-zeebe-"+camundaVersion+"\\bin\\camunda.bat", extraArgs)
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
