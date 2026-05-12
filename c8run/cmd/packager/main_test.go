package main

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestSyncEnvFile(t *testing.T) {
	// given
	tempDir := t.TempDir()
	workingDir, err := os.Getwd()
	require.NoError(t, err)
	t.Cleanup(func() {
		require.NoError(t, os.Chdir(workingDir))
	})
	require.NoError(t, os.Chdir(tempDir))

	// when
	err = syncEnvFile("8.10.0-SNAPSHOT", "8.10.0-SNAPSHOT")

	// then
	require.NoError(t, err)
	content, err := os.ReadFile(filepath.Join(tempDir, ".env"))
	require.NoError(t, err)
	require.Equal(
		t,
		"CAMUNDA_VERSION=8.10.0-SNAPSHOT\nCONNECTORS_VERSION=8.10.0-SNAPSHOT\n",
		string(content),
	)
}
