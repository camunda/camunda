package processmanagement

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

type mockC8 struct {
	ProcessTreeFunc func(pid int) []*os.Process
}

func (m *mockC8) OpenBrowser(ctx context.Context, protocol string, port int) error { return nil }
func (m *mockC8) ProcessTree(pid int) []*os.Process {
	if m.ProcessTreeFunc != nil {
		return m.ProcessTreeFunc(pid)
	}
	return nil
}
func (m *mockC8) VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd { return nil }
func (m *mockC8) ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd {
	return nil
}

func (m *mockC8) ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	return nil
}

func (m *mockC8) CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	return nil
}

type testHandler struct {
	ProcessHandler
}

func TestWriteAndReadPIDFromFile(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "test.pid")
	h := &ProcessHandler{}
	pid := 12345

	err := h.WritePIDToFile(pidPath, pid)
	if err != nil {
		t.Fatalf("WritePIDToFile failed: %v", err)
	}

	readPid, err := h.ReadPIDFromFile(pidPath)
	if err != nil {
		t.Fatalf("ReadPIDFromFile failed: %v", err)
	}
	if readPid != pid {
		t.Errorf("Expected pid %d, got %d", pid, readPid)
	}
}

func TestReadPIDFromFile_InvalidPID(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "bad.pid")
	if err := os.WriteFile(pidPath, []byte("notanumber"), 0644); err != nil {
		t.Fatalf("Failed to write invalid PID file: %v", err)
	}
	h := &ProcessHandler{}
	_, err := h.ReadPIDFromFile(pidPath)
	if err == nil {
		t.Error("Expected error for invalid PID, got nil")
	}
}

func TestCleanUp_RemovesPIDFile(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "cleanup.pid")
	if err := os.WriteFile(pidPath, []byte("123"), 0644); err != nil {
		t.Fatalf("Failed to write PID file: %v", err)
	}
	h := &ProcessHandler{}
	h.cleanUp(pidPath)
	if _, err := os.Stat(pidPath); !os.IsNotExist(err) {
		t.Error("PID file was not removed by cleanUp")
	}
}

func TestAttemptToStartProcess_StartsWhenNoPIDFile(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "no.pid")
	started := false
	h := &testHandler{
		ProcessHandler: ProcessHandler{C8: &mockC8{ProcessTreeFunc: func(pid int) []*os.Process { return nil }}},
	}

	startProcess := func() { started = true }
	healthCheck := func() error { return nil }
	stop := func() {}

	h.AttemptToStartProcess(pidPath, "testproc", startProcess, healthCheck, stop)
	if !started {
		t.Error("Process was not started when no PID file present")
	}
}

func TestAttemptToStartProcess_CleansUpStalePIDAndStarts(t *testing.T) {
	dir := t.TempDir()
	pidPath := filepath.Join(dir, "stale.pid")

	if err := os.WriteFile(pidPath, []byte("123"), 0644); err != nil {
		t.Fatalf("Failed to write PID file: %v", err)
	}

	started := false
	stopped := false

	h := &testHandler{
		ProcessHandler: ProcessHandler{C8: &mockC8{ProcessTreeFunc: func(pid int) []*os.Process { return []*os.Process{{Pid: 123}} }}},
	}

	startProcess := func() { started = true }
	healthCheck := func() error { return nil }
	stop := func() { stopped = true }

	h.AttemptToStartProcess(pidPath, "testproc", startProcess, healthCheck, stop)
	if !started {
		t.Error("Process was not started when no PID file present")
	}
	if stopped {
		t.Error("Stop should not be called. This should only be called when everything fails. Its like a panic but allows for the shutdown logic to run")
	}
}
