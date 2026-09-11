/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package overrides

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func writeConfig(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "application.yaml")
	require.NoError(t, os.WriteFile(path, []byte(content), 0644))
	return path
}

func TestConnectorsAuthRequired(t *testing.T) {
	tests := []struct {
		name     string
		content  string
		expected bool
	}{
		{
			name: "authorizations enabled",
			content: `
camunda:
  security:
    authorizations:
      enabled: true
`,
			expected: true,
		},
		{
			name: "api protected",
			content: `
camunda:
  security:
    authentication:
      unprotected-api: false
`,
			expected: true,
		},
		{
			name: "authorizations disabled and api unprotected",
			content: `
camunda:
  security:
    authentication:
      unprotected-api: true
    authorizations:
      enabled: false
`,
			expected: false,
		},
		{
			name:     "security section missing",
			content:  "camunda:\n  data: {}\n",
			expected: false,
		},
		{
			name:     "malformed yaml",
			content:  "camunda: [this is not valid",
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			path := writeConfig(t, tt.content)
			assert.Equal(t, tt.expected, ConnectorsAuthRequired(path))
		})
	}
}

func TestConnectorsAuthRequiredWhenConfigMissing(t *testing.T) {
	assert.False(t, ConnectorsAuthRequired(""))
	assert.False(t, ConnectorsAuthRequired(filepath.Join(t.TempDir(), "does-not-exist.yaml")))
}

func TestSetConnectorsAuthEnvVarsSetsCredentialsWhenRequired(t *testing.T) {
	path := writeConfig(t, `
camunda:
  security:
    authorizations:
      enabled: true
`)
	t.Setenv("CAMUNDA_CLIENT_AUTH_USERNAME", "")
	t.Setenv("CAMUNDA_CLIENT_AUTH_PASSWORD", "")

	settings := types.C8RunSettings{
		ResolvedConfigPath: path,
		Username:           "operator",
		Password:           "s3cret",
	}

	require.NoError(t, SetConnectorsAuthEnvVars(settings))
	assert.Equal(t, "operator", os.Getenv("CAMUNDA_CLIENT_AUTH_USERNAME"))
	assert.Equal(t, "s3cret", os.Getenv("CAMUNDA_CLIENT_AUTH_PASSWORD"))
}

func TestSetConnectorsAuthEnvVarsSkippedWhenNotRequired(t *testing.T) {
	path := writeConfig(t, `
camunda:
  security:
    authentication:
      unprotected-api: true
    authorizations:
      enabled: false
`)
	t.Setenv("CAMUNDA_CLIENT_AUTH_USERNAME", "")
	t.Setenv("CAMUNDA_CLIENT_AUTH_PASSWORD", "")

	settings := types.C8RunSettings{
		ResolvedConfigPath: path,
		Username:           "demo",
		Password:           "demo",
	}

	require.NoError(t, SetConnectorsAuthEnvVars(settings))
	assert.Empty(t, os.Getenv("CAMUNDA_CLIENT_AUTH_USERNAME"))
	assert.Empty(t, os.Getenv("CAMUNDA_CLIENT_AUTH_PASSWORD"))
}

func TestSetConnectorsAuthEnvVarsDoesNotOverrideExistingValues(t *testing.T) {
	path := writeConfig(t, `
camunda:
  security:
    authorizations:
      enabled: true
`)
	t.Setenv("CAMUNDA_CLIENT_AUTH_USERNAME", "preset-user")
	t.Setenv("CAMUNDA_CLIENT_AUTH_PASSWORD", "preset-pass")

	settings := types.C8RunSettings{
		ResolvedConfigPath: path,
		Username:           "operator",
		Password:           "s3cret",
	}

	require.NoError(t, SetConnectorsAuthEnvVars(settings))
	assert.Equal(t, "preset-user", os.Getenv("CAMUNDA_CLIENT_AUTH_USERNAME"))
	assert.Equal(t, "preset-pass", os.Getenv("CAMUNDA_CLIENT_AUTH_PASSWORD"))
}
