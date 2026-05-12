/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSyncEnvFileWritesResolvedVersions(t *testing.T) {
	// given
	path := filepath.Join(t.TempDir(), ".env")

	// when
	err := syncEnvFile(path, "8.10.0-SNAPSHOT", "8.10.0-SNAPSHOT")

	// then
	require.NoError(t, err)
	content, err := os.ReadFile(path)
	require.NoError(t, err)
	assert.Contains(t, string(content), "CAMUNDA_VERSION=8.10.0-SNAPSHOT")
	assert.Contains(t, string(content), "CONNECTORS_VERSION=8.10.0-SNAPSHOT")
}

func TestSyncEnvFileOverwritesStaleVersion(t *testing.T) {
	// given
	path := filepath.Join(t.TempDir(), ".env")
	require.NoError(t, os.WriteFile(path, []byte("CAMUNDA_VERSION=8.9.0-alpha5\nCONNECTORS_VERSION=8.9.0\n"), 0644))

	// when
	err := syncEnvFile(path, "8.10.0-SNAPSHOT", "8.10.0-SNAPSHOT")

	// then
	require.NoError(t, err)
	content, err := os.ReadFile(path)
	require.NoError(t, err)
	assert.Contains(t, string(content), "CAMUNDA_VERSION=8.10.0-SNAPSHOT")
	assert.NotContains(t, string(content), "8.9.0-alpha5")
}

func TestSyncEnvFileReturnsErrorOnUnwritablePath(t *testing.T) {
	// given - nonexistent parent directory forces write failure
	path := filepath.Join(t.TempDir(), "nonexistent", ".env")

	// when
	err := syncEnvFile(path, "8.10.0-SNAPSHOT", "8.10.0-SNAPSHOT")

	// then
	assert.Error(t, err)
}
