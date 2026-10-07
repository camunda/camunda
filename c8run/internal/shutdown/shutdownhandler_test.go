package shutdown

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/camunda/camunda/c8run/internal/springconfig"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/require"
)

func TestShouldDeleteDataDirUsesEnvOverride(t *testing.T) {
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL", " jdbc:h2:mem:testdb ")
	settings := types.C8RunSettings{SecondaryStorageType: "rdbms"}

	require.True(t, shouldDeleteDataDir(settings))
}

func TestShouldDeleteDataDirIgnoresNonH2EnvOverride(t *testing.T) {
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL", "jdbc:postgres://localhost")
	settings := types.C8RunSettings{SecondaryStorageType: "rdbms"}

	require.False(t, shouldDeleteDataDir(settings))
}

func TestShouldDeleteDataDirUsesCustomConfigDirectory(t *testing.T) {
	unsetEnv(t, "CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL")
	baseDir := t.TempDir()
	userConfigDir := filepath.Join(baseDir, "my-config")
	configPath := filepath.Join(userConfigDir, "application.yaml")
	writeConfig(t, configPath, `
camunda:
  data:
    secondary-storage:
      rdbms:
        url: jdbc:h2:mem:foo
`)

	settings := types.C8RunSettings{
		SecondaryStorageType: "rdbms",
		ConfigPaths:          springconfig.Paths(baseDir, "my-config"),
	}

	require.True(t, shouldDeleteDataDir(settings))
}

func TestShouldDeleteDataDirSkipsForDefaultFileBasedConfig(t *testing.T) {
	unsetEnv(t, "CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL")
	baseDir := t.TempDir()
	defaultConfig := filepath.Join(baseDir, "configuration", "application.yaml")
	writeConfig(t, defaultConfig, `
camunda:
  data:
    secondary-storage:
      rdbms:
        url: jdbc:h2:file:./foo
`)

	settings := types.C8RunSettings{SecondaryStorageType: "rdbms", ConfigPaths: springconfig.Paths(baseDir, "")}

	require.False(t, shouldDeleteDataDir(settings))
}

func TestShouldDeleteDataDirHandlesMissingOrMalformedConfig(t *testing.T) {
	unsetEnv(t, "CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL")
	baseDir := t.TempDir()
	settings := types.C8RunSettings{SecondaryStorageType: "rdbms", ConfigPaths: springconfig.Paths(baseDir, "")}

	require.False(t, shouldDeleteDataDir(settings), "missing config should not trigger deletion")

	malformedConfig := filepath.Join(baseDir, "configuration", "application.yaml")
	writeConfig(t, malformedConfig, ":\n  bad yaml")

	require.False(t, shouldDeleteDataDir(settings), "malformed config should not trigger deletion")
}

func TestShouldDeleteDataDirSkipsWhenSecondaryStorageNotExplicitlySet(t *testing.T) {
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL", "jdbc:h2:mem:foo")
	settings := types.C8RunSettings{SecondaryStorageType: ""}

	require.False(t, shouldDeleteDataDir(settings))

	settings = types.C8RunSettings{SecondaryStorageType: "elasticsearch"}
	require.False(t, shouldDeleteDataDir(settings))
}

func TestDeleteDataDirRemovesH2Data(t *testing.T) {
	baseDir := t.TempDir()
	version := "8.9.0"
	dataDir := filepath.Join(baseDir, "camunda-zeebe-"+version, "data")
	require.NoError(t, os.MkdirAll(dataDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(dataDir, "dummy.txt"), []byte("value"), 0o644))

	processes := types.Processes{
		Camunda: types.Process{
			Version: version,
			PidPath: filepath.Join(baseDir, "camunda.process"),
		},
	}

	deleteDataDir(processes)

	_, err := os.Stat(dataDir)
	require.True(t, os.IsNotExist(err), "data directory should be removed for H2 cleanup")
}

func TestDeleteDataDirPreservesLocalSecrets(t *testing.T) {
	baseDir := t.TempDir()
	version := "8.10.0"
	dataDir := filepath.Join(baseDir, "camunda-zeebe-"+version, "data")
	secretPath := filepath.Join(baseDir, "secrets", "API_KEY")
	require.NoError(t, os.MkdirAll(dataDir, 0o755))
	require.NoError(t, os.MkdirAll(filepath.Dir(secretPath), 0o700))
	require.NoError(t, os.WriteFile(secretPath, []byte("secret-value"), 0o600))
	processes := types.Processes{
		Camunda: types.Process{
			Version: version,
			PidPath: filepath.Join(baseDir, "camunda.process"),
		},
	}

	deleteDataDir(processes)

	_, err := os.Stat(secretPath)
	require.NoError(t, err, "local secret should remain after H2 data cleanup")
}

func TestDeleteDataDirSkipsWhenVersionEmpty(t *testing.T) {
	baseDir := t.TempDir()
	targetDir := filepath.Join(baseDir, "camunda-zeebe-", "data")
	require.NoError(t, os.MkdirAll(targetDir, 0o755))

	processes := types.Processes{
		Camunda: types.Process{
			Version: "",
			PidPath: filepath.Join(baseDir, "camunda.process"),
		},
	}

	deleteDataDir(processes)

	_, err := os.Stat(targetDir)
	require.NoError(t, err, "data directory should remain because version is empty")
}

func TestDeleteDataDirSkipsWhenVersionContainsPathTraversal(t *testing.T) {
	baseDir := t.TempDir()
	version := "../evil"
	safeDir := filepath.Join(baseDir, "camunda-zeebe-safe", "data")
	require.NoError(t, os.MkdirAll(safeDir, 0o755))
	sentinel := filepath.Join(safeDir, "sentinel.txt")
	require.NoError(t, os.WriteFile(sentinel, []byte("keep"), 0o644))

	processes := types.Processes{
		Camunda: types.Process{
			Version: version,
			PidPath: filepath.Join(baseDir, "camunda.process"),
		},
	}

	deleteDataDir(processes)

	_, err := os.Stat(sentinel)
	require.NoError(t, err, "sentinel file should remain because version contained path traversal characters")
}

func unsetEnv(t *testing.T, key string) {
	t.Helper()
	oldValue, had := os.LookupEnv(key)
	if had {
		t.Cleanup(func() {
			_ = os.Setenv(key, oldValue)
		})
	} else {
		t.Cleanup(func() {
			_ = os.Unsetenv(key)
		})
	}
	require.NoError(t, os.Unsetenv(key))
}

func writeConfig(t *testing.T, path string, content string) {
	t.Helper()
	require.NoError(t, os.MkdirAll(filepath.Dir(path), 0o755))
	require.NoError(t, os.WriteFile(path, []byte(content), 0o644))
}

func TestShouldDeleteDataDirReadsPropertiesInConfigDirectory(t *testing.T) {
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL", "")
	baseDir := t.TempDir()
	cfg := filepath.Join(baseDir, "cfg")
	require.NoError(t, os.MkdirAll(cfg, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(cfg, "application.properties"),
		[]byte("camunda.data.secondary-storage.rdbms.url=jdbc:h2:mem:testdb\n"), 0o644))
	settings := types.C8RunSettings{SecondaryStorageType: "rdbms", ConfigPaths: springconfig.Paths(baseDir, "cfg")}
	require.True(t, shouldDeleteDataDir(settings))
}
