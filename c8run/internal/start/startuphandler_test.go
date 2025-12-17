package start

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

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
