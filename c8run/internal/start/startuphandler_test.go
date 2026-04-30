package start

import (
	"errors"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"
)

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
