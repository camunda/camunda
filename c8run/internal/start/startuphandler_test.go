package start

import (
	"context"
	"errors"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"strings"
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

	resolvedHome, resolvedBinary, err := resolveJavaHomeAndBinary(t.TempDir())
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

func TestResolveJavaHomeAndBinaryUsesBundledJREFirst(t *testing.T) {
	// given
	parentDir := t.TempDir()
	bundledJavaHome := filepath.Join(parentDir, "jre")
	bundledJavaBin := filepath.Join(bundledJavaHome, "bin")
	if err := os.MkdirAll(bundledJavaBin, 0o755); err != nil {
		t.Fatalf("failed to create bundled java bin directory: %v", err)
	}

	javaBinaryName := "java"
	if runtime.GOOS == "windows" {
		javaBinaryName = "java.exe"
	}
	bundledJavaBinary := filepath.Join(bundledJavaBin, javaBinaryName)
	if err := os.WriteFile(bundledJavaBinary, []byte("echo bundled java\n"), 0o755); err != nil {
		t.Fatalf("failed to create bundled java binary placeholder: %v", err)
	}

	externalJavaHome := filepath.Join(t.TempDir(), "external-java-home")
	if err := os.MkdirAll(filepath.Join(externalJavaHome, "bin"), 0o755); err != nil {
		t.Fatalf("failed to create external java home: %v", err)
	}
	t.Setenv("JAVA_HOME", externalJavaHome)

	// when
	resolvedHome, resolvedBinary, err := resolveJavaHomeAndBinary(parentDir)

	// then
	if err != nil {
		t.Fatalf("resolveJavaHomeAndBinary returned error: %v", err)
	}
	if resolvedHome != bundledJavaHome {
		t.Fatalf("expected bundled JAVA_HOME %s, got %s", bundledJavaHome, resolvedHome)
	}
	if resolvedBinary != bundledJavaBinary {
		t.Fatalf("expected bundled java binary %s, got %s", bundledJavaBinary, resolvedBinary)
	}
}

func TestConfigureJavaRuntimeEnvironment(t *testing.T) {
	// given
	t.Setenv("JAVA_HOME", "")
	t.Setenv("JAVACMD", "")
	javaHome := filepath.Join(t.TempDir(), "jre")
	javaBinary := filepath.Join(javaHome, "bin", "java")

	// when
	err := configureJavaRuntimeEnvironment(javaHome, javaBinary)

	// then
	if err != nil {
		t.Fatalf("configureJavaRuntimeEnvironment returned error: %v", err)
	}
	if os.Getenv("JAVA_HOME") != javaHome {
		t.Fatalf("expected JAVA_HOME %s, got %s", javaHome, os.Getenv("JAVA_HOME"))
	}
	expectedJavaCmd := javaBinary
	if runtime.GOOS == "windows" {
		expectedJavaCmd = `"` + javaBinary + `"`
	}
	if os.Getenv("JAVACMD") != expectedJavaCmd {
		t.Fatalf("expected JAVACMD %s, got %s", expectedJavaCmd, os.Getenv("JAVACMD"))
	}
}

func TestParseJavaMajorVersion(t *testing.T) {
	tests := []struct {
		name            string
		version         string
		expectedVersion int
	}{
		{
			name:            "modern version",
			version:         "25.0.1",
			expectedVersion: 25,
		},
		{
			name:            "early access version",
			version:         "25-ea",
			expectedVersion: 25,
		},
		{
			name:            "legacy version",
			version:         "1.8.0_412",
			expectedVersion: 8,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// when
			actualVersion, err := parseJavaMajorVersion(tt.version)

			// then
			if err != nil {
				t.Fatalf("parseJavaMajorVersion returned error: %v", err)
			}
			if actualVersion != tt.expectedVersion {
				t.Fatalf("expected Java major version %d, got %d", tt.expectedVersion, actualVersion)
			}
		})
	}
}

func TestConfigureJavaCompatibilityOptionsAddsJava25Options(t *testing.T) {
	// given
	t.Setenv(jdkJavaOptionsEnvironment, "-Dexisting=true")

	// when
	err := configureJavaCompatibilityOptions(25)

	// then
	if err != nil {
		t.Fatalf("configureJavaCompatibilityOptions returned error: %v", err)
	}
	actualOptions := os.Getenv(jdkJavaOptionsEnvironment)
	for _, expectedOption := range []string{"-Dexisting=true", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"} {
		if !strings.Contains(actualOptions, expectedOption) {
			t.Fatalf("expected %s in %s", expectedOption, actualOptions)
		}
	}
}

func TestConfigureJavaCompatibilityOptionsSkipsOlderJavaVersions(t *testing.T) {
	// given
	t.Setenv(jdkJavaOptionsEnvironment, "-Dexisting=true")

	// when
	err := configureJavaCompatibilityOptions(21)

	// then
	if err != nil {
		t.Fatalf("configureJavaCompatibilityOptions returned error: %v", err)
	}
	if os.Getenv(jdkJavaOptionsEnvironment) != "-Dexisting=true" {
		t.Fatalf("expected existing options to be unchanged, got %s", os.Getenv(jdkJavaOptionsEnvironment))
	}
}

func TestConfigureJavaCompatibilityOptionsDoesNotDuplicateJava25Options(t *testing.T) {
	// given
	options := "--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
	t.Setenv(jdkJavaOptionsEnvironment, options)

	// when
	err := configureJavaCompatibilityOptions(25)

	// then
	if err != nil {
		t.Fatalf("configureJavaCompatibilityOptions returned error: %v", err)
	}
	if os.Getenv(jdkJavaOptionsEnvironment) != options {
		t.Fatalf("expected options to be unchanged, got %s", os.Getenv(jdkJavaOptionsEnvironment))
	}
}
