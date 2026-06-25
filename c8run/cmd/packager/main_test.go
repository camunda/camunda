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

func TestSyncEnvFilePreservesOtherKeysAndComments(t *testing.T) {
	// given - an 8.8-style .env carrying compose/elasticsearch keys and comments
	path := filepath.Join(t.TempDir(), ".env")
	original := "ELASTICSEARCH_VERSION=8.17.3\n" +
		"# this is the version of camunda/ zeebe\n" +
		"CAMUNDA_VERSION=8.8.28\n" +
		"# docker images moved to a shortened tag, override compose with this value when using --docker\n" +
		"CAMUNDA_DOCKER_VERSION=8.8.28\n" +
		"CONNECTORS_VERSION=8.8.14\n" +
		"# version of docker compose artifact (used for --docker option)\n" +
		"COMPOSE_TAG=8.8\n" +
		"COMPOSE_EXTRACTED_FOLDER=docker-compose-8.8\n"
	require.NoError(t, os.WriteFile(path, []byte(original), 0644))

	// when
	err := syncEnvFile(path, "8.8.29", "8.8.15")

	// then
	require.NoError(t, err)
	content, err := os.ReadFile(path)
	require.NoError(t, err)
	got := string(content)
	// the two supplied keys are updated in place; the old version line is gone
	assert.Contains(t, got, "CAMUNDA_VERSION=8.8.29")
	assert.Contains(t, got, "CONNECTORS_VERSION=8.8.15")
	assert.NotContains(t, got, "CAMUNDA_VERSION=8.8.28")
	assert.NotContains(t, got, "CONNECTORS_VERSION=8.8.14")
	// every other key survives untouched — including CAMUNDA_DOCKER_VERSION, whose name shares the
	// CAMUNDA_VERSION prefix and whose value matches the old CAMUNDA_VERSION (guards a prefix-match bug)
	assert.Contains(t, got, "CAMUNDA_DOCKER_VERSION=8.8.28")
	assert.Contains(t, got, "ELASTICSEARCH_VERSION=8.17.3")
	assert.Contains(t, got, "COMPOSE_TAG=8.8")
	assert.Contains(t, got, "COMPOSE_EXTRACTED_FOLDER=docker-compose-8.8")
	// comments survive
	assert.Contains(t, got, "# version of docker compose artifact (used for --docker option)")
	// updated keys stay in their original positions (not moved/appended) and nothing else shifts
	expected := "ELASTICSEARCH_VERSION=8.17.3\n" +
		"# this is the version of camunda/ zeebe\n" +
		"CAMUNDA_VERSION=8.8.29\n" +
		"# docker images moved to a shortened tag, override compose with this value when using --docker\n" +
		"CAMUNDA_DOCKER_VERSION=8.8.28\n" +
		"CONNECTORS_VERSION=8.8.15\n" +
		"# version of docker compose artifact (used for --docker option)\n" +
		"COMPOSE_TAG=8.8\n" +
		"COMPOSE_EXTRACTED_FOLDER=docker-compose-8.8\n"
	assert.Equal(t, expected, got)
}

func TestSyncEnvFilePreservesCRLFLineEndings(t *testing.T) {
	// given - a CRLF .env
	path := filepath.Join(t.TempDir(), ".env")
	require.NoError(t, os.WriteFile(path, []byte("CAMUNDA_VERSION=8.9.0\r\nCONNECTORS_VERSION=8.9.0\r\nELASTICSEARCH_VERSION=8.17.3\r\n"), 0644))

	// when
	err := syncEnvFile(path, "8.9.1", "8.9.1")

	// then - updated keys keep CRLF; no line is silently downgraded to bare LF
	require.NoError(t, err)
	content, err := os.ReadFile(path)
	require.NoError(t, err)
	expected := "CAMUNDA_VERSION=8.9.1\r\nCONNECTORS_VERSION=8.9.1\r\nELASTICSEARCH_VERSION=8.17.3\r\n"
	assert.Equal(t, expected, string(content))
}

func TestSyncEnvFileAppendsMissingKey(t *testing.T) {
	// given - a .env that only pins CAMUNDA_VERSION
	path := filepath.Join(t.TempDir(), ".env")
	require.NoError(t, os.WriteFile(path, []byte("CAMUNDA_VERSION=8.9.0\n"), 0644))

	// when
	err := syncEnvFile(path, "8.9.1", "8.9.1")

	// then
	require.NoError(t, err)
	content, err := os.ReadFile(path)
	require.NoError(t, err)
	got := string(content)
	assert.Contains(t, got, "CAMUNDA_VERSION=8.9.1")
	assert.Contains(t, got, "CONNECTORS_VERSION=8.9.1")
}
