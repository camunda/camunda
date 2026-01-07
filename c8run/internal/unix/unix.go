//go:build linux || darwin

package unix

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"syscall"

	"github.com/camunda/camunda/c8run/internal/connectors"
)

func (w *UnixC8Run) OpenBrowser(ctx context.Context, url string) error {
	var openBrowserCmdString string
	switch runtime.GOOS {
	case "darwin":
		openBrowserCmdString = "open"
	case "linux":
		openBrowserCmdString = "xdg-open"
	default:
		return fmt.Errorf("OpenBrowser: platform %s is not supported", runtime.GOOS)
	}
	openBrowserCmd := exec.CommandContext(ctx, openBrowserCmdString, url)
	err := openBrowserCmd.Run()
	if err != nil {
		return fmt.Errorf("OpenBrowser: failed to open browser %w\n%s", err, debug.Stack())
	}
	return nil
}

func (w *UnixC8Run) ProcessTree(commandPid int) []int {
	// For unix systems we can kill all processes within a process group by setting a pgid
	// therefore, only the main process pid is required.
	return []int{commandPid}
}

func (w *UnixC8Run) VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd {
	return exec.CommandContext(ctx, javaBinaryPath, "--version")
}

func (w *UnixC8Run) ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd {
	elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch")
	elasticsearchCmd := exec.CommandContext(
		ctx,
		elasticsearchCmdString,
		"-E", "xpack.ml.enabled=false",
		"-E", "xpack.security.enabled=false",
		"-E", "discovery.type=single-node",
		"-E", "cluster.routing.allocation.disk.threshold_enabled=false",
	)
	elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return elasticsearchCmd
}

func (w *UnixC8Run) ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string, camundaPort int) *exec.Cmd {
	classPath := parentDir + "/*:" + parentDir + "/custom_connectors/*"
	mainClass := "io.camunda.connector.runtime.app.ConnectorRuntimeApplication"
	if connectors.UsePropertiesLauncher() {
		mainClass = "org.springframework.boot.loader.launch.PropertiesLauncher"
	}
	springConfigLocation := "--spring.config.additional-location=" + parentDir + "/connectors-application.properties"
	connectorsCmd := exec.CommandContext(ctx, javaBinary, "-cp", classPath, mainClass, springConfigLocation)
	connectorsCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}

	// Set default Zeebe REST address if the user has not provided one already.
	if _, exists := os.LookupEnv("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS"); !exists {
		zeebeRestAddress := fmt.Sprintf("http://localhost:%d", camundaPort)
		connectorsCmd.Env = append(os.Environ(), fmt.Sprintf("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=%s", zeebeRestAddress))
	}

	return connectorsCmd
}

func (w *UnixC8Run) CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	camundaCmdString := parentDir + "/camunda-zeebe-" + camundaVersion + "/bin/camunda"
	camundaCmd := exec.CommandContext(ctx, camundaCmdString, extraArgs)
	if javaOpts != "" {
		camundaCmd.Env = append(os.Environ(), "JAVA_OPTS="+javaOpts)
	}
	camundaCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return camundaCmd
}
