package packages

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestShouldNotArchiveDockerComposeArtifacts(t *testing.T) {
	// given
	filesToArchive := getFilesToArchive("linux", "connector.jar", "8.9.0")

	// when
	for _, path := range filesToArchive {
		if strings.Contains(path, "docker-compose") {
			t.Fatalf("expected docker compose artifacts to be excluded, found %s", path)
		}
	}

	// then
	expectedFiles := []string{
		filepath.Join("c8run", "README.md"),
		filepath.Join("c8run", "camunda-zeebe-8.9.0"),
		filepath.Join("c8run", "start.sh"),
		filepath.Join("c8run", "shutdown.sh"),
	}
	for _, expected := range expectedFiles {
		if !contains(filesToArchive, expected) {
			t.Fatalf("expected archived files to include %s", expected)
		}
	}
}

func TestShouldRemoveLegacyDockerComposeArtifactsDuringClean(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	tempDir := t.TempDir()
	if err := os.Chdir(tempDir); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	t.Cleanup(func() { _ = os.Chdir(cwd) })

	composeDir := filepath.Join(tempDir, "docker-compose-8.9")
	if err := os.MkdirAll(composeDir, 0o755); err != nil {
		t.Fatalf("failed to create compose directory: %v", err)
	}
	composeZip := filepath.Join(tempDir, "docker-compose-8.9.zip")
	if err := os.WriteFile(composeZip, []byte("legacy"), 0o644); err != nil {
		t.Fatalf("failed to create compose zip: %v", err)
	}

	// when
	Clean("8.9.0")

	// then
	if _, err := os.Stat(composeDir); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("expected compose directory to be removed, got err=%v", err)
	}
	if _, err := os.Stat(composeZip); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("expected compose zip to be removed, got err=%v", err)
	}
}

func contains(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
