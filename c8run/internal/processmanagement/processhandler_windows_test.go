//go:build windows

package processmanagement

import (
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"testing"
)

func TestAttemptToStartProcess_KillsUnhealthyProcess(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "unhealthy.pid")

	// Use a Windows-compatible long-running command.
	// `ping -n 10 127.0.0.1` sends 10 pings (roughly ~10 seconds)
	// which is sufficient to keep the process alive for the duration of the test.
	cmd := exec.Command("ping", "-n", "10", "127.0.0.1")
	if err := cmd.Start(); err != nil {
		t.Fatalf("Failed to start test process: %v", err)
	}
	proc := cmd.Process
	defer func() {
		_ = cmd.Wait()
	}()

	if err := os.WriteFile(pidPath, []byte(strconv.Itoa(proc.Pid)), 0644); err != nil {
		t.Fatalf("Failed to write PID file: %v", err)
	}

	killed := false
	started := false
	stopped := false

	h := &testHandler{
		ProcessHandler: ProcessHandler{C8: &mockC8{ProcessTreeFunc: func(pid int) []*os.Process { return []*os.Process{proc} }}},
	}

	startProcess := func() { started = true }
	firstHealthCheck := true
	healthCheck := func() error {
		if firstHealthCheck {
			firstHealthCheck = false
			return errors.New("unhealthy")
		}
		killed = true
		return nil
	}
	stop := func() { stopped = true }

	h.AttemptToStartProcess(pidPath, "testproc", startProcess, healthCheck, stop)
	if !killed {
		t.Error("Unhealthy process was not killed")
	}
	if !started {
		t.Error("Process was not restarted after being killed")
	}
	if stopped {
		t.Error("Stop was called. This should only be called when everything fails. Its like a panic but allows for the shutdown logic to run")
	}
}
