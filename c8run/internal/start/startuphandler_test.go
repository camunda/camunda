package start

import (
	"context"
	"errors"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
)

type stubProcessHandler struct {
	attemptCount int
	lastPidPath  string
	lastName     string
}

func (s *stubProcessHandler) AttemptToStartProcess(pidPath string, processName string, startProcess func(), healthCheck func() error, stop context.CancelFunc) {
	s.attemptCount++
	s.lastPidPath = pidPath
	s.lastName = processName
}

func (s *stubProcessHandler) WritePIDToFile(pidPath string, pid int) error {
	return nil
}

func (s *stubProcessHandler) TrackProcessTree(pidPath string, rootPid int) {}

func TestEnsurePortAvailable(t *testing.T) {
	inUseListener, err := net.Listen("tcp4", ":0")
	if err != nil {
		t.Fatalf("failed to get ephemeral listener: %v", err)
	}

	port := inUseListener.Addr().(*net.TCPAddr).Port

	if err := ensurePortAvailable(port); err == nil {
		t.Fatalf("expected error when port %d is already bound", port)
	}

	if err := inUseListener.Close(); err != nil {
		t.Fatalf("failed to close temporary listener: %v", err)
	}
	time.Sleep(25 * time.Millisecond) // give the OS a moment to release the port

	if err := ensurePortAvailable(port); err != nil {
		t.Fatalf("expected port %d to be reported as available: %v", port, err)
	}
}

func TestResolveJavaHomeAndBinaryKeepsCustomPathWhenSymlinkResolutionFails(t *testing.T) {
	t.Setenv("JAVA_HOME", "")

	javaHome := filepath.Join(t.TempDir(), "java-home")
	binDir := filepath.Join(javaHome, "bin")
	if err := os.MkdirAll(binDir, 0o755); err != nil {
		t.Fatalf("failed to create bin directory: %v", err)
	}

	javaBinaryName := "java"
	if runtime.GOOS == "windows" {
		javaBinaryName = "java.exe"
	}
	expectedJavaBinary := filepath.Join(binDir, javaBinaryName)
	if err := os.WriteFile(expectedJavaBinary, []byte("echo java\n"), 0o755); err != nil {
		t.Fatalf("failed to create java binary placeholder: %v", err)
	}

	t.Setenv("JAVA_HOME", javaHome)

	originalEvalSymlinks := evalSymlinks
	defer func() {
		evalSymlinks = originalEvalSymlinks
	}()
	evalSymlinks = func(path string) (string, error) {
		return "", errors.New("forced symlink failure")
	}

	resolvedHome, resolvedBinary, err := resolveJavaHomeAndBinary()
	if err != nil {
		t.Fatalf("resolveJavaHomeAndBinary returned error: %v", err)
	}

	if resolvedHome != javaHome {
		t.Fatalf("expected JAVA_HOME %s, got %s", javaHome, resolvedHome)
	}

	if resolvedBinary != expectedJavaBinary {
		t.Fatalf("expected java binary %s, got %s", expectedJavaBinary, resolvedBinary)
	}
}

func TestShouldSkipConnectorsStartupWhenDisabled(t *testing.T) {
	// given
	handler := &stubProcessHandler{}
	startupHandler := &StartupHandler{ProcessHandler: handler}
	state := &types.State{
		Settings: types.C8RunSettings{
			DisableConnectors: true,
		},
		ProcessInfo: types.Processes{
			Connectors: types.Process{
				PidPath: "connectors.process",
			},
		},
	}

	// when
	startupHandler.startConnectors(context.Background(), func() {}, state, t.TempDir(), "java")

	// then
	if handler.attemptCount != 0 {
		t.Fatalf("expected connectors startup to be skipped, but AttemptToStartProcess was called %d times", handler.attemptCount)
	}
}

func TestShouldAttemptConnectorsStartupWhenEnabled(t *testing.T) {
	// given
	handler := &stubProcessHandler{}
	startupHandler := &StartupHandler{ProcessHandler: handler}
	state := &types.State{
		Settings: types.C8RunSettings{
			Port: 8080,
		},
		ProcessInfo: types.Processes{
			Connectors: types.Process{
				PidPath: "connectors.process",
			},
		},
	}

	// when
	startupHandler.startConnectors(context.Background(), func() {}, state, t.TempDir(), "java")

	// then
	if handler.attemptCount != 1 {
		t.Fatalf("expected connectors startup to be attempted once, but got %d attempts", handler.attemptCount)
	}
	if handler.lastPidPath != "connectors.process" {
		t.Fatalf("expected pid path connectors.process, got %s", handler.lastPidPath)
	}
	if handler.lastName != "Connectors" {
		t.Fatalf("expected process name Connectors, got %s", handler.lastName)
	}
}
