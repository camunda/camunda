/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package start

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTenantConnectorsEnvBindsTenantAndPort(t *testing.T) {
	tenant := types.PhysicalTenant{ID: "sales", Username: "alice", Password: "pw", Connectors: true, ConnectorsPort: 8087}
	env := TenantConnectorsEnv(tenant, types.C8RunSettings{})
	assert.Equal(t, []string{"SERVER_PORT=8087", "CAMUNDA_CLIENT_PHYSICALTENANTID=sales"}, env)
}

func TestTenantConnectorsEnvAddsCredentialsWhenAuthRequired(t *testing.T) {
	cfg := filepath.Join(t.TempDir(), "application.yaml")
	require.NoError(t, os.WriteFile(cfg, []byte("camunda:\n  security:\n    authorizations:\n      enabled: true\n"), 0o644))
	tenant := types.PhysicalTenant{ID: "sales", Username: "alice", Password: "pw", ConnectorsPort: 8087}
	env := TenantConnectorsEnv(tenant, types.C8RunSettings{ConfigPaths: []string{cfg}})
	assert.Contains(t, env, "CAMUNDA_CLIENT_AUTH_USERNAME=alice")
	assert.Contains(t, env, "CAMUNDA_CLIENT_AUTH_PASSWORD=pw")
}

func TestTenantConnectorsPidPath(t *testing.T) {
	assert.Equal(t, filepath.Join("base", "connectors-sales.process"), TenantConnectorsPidPath("base", "sales"))
}

func TestConnectorsEnvDropsTenantProperties(t *testing.T) {
	t.Setenv("CAMUNDA_PHYSICALTENANTS_HR_SECURITY_INITIALIZATION_USERS_0_PASSWORD", "secret")
	for _, kv := range connectorsEnv(nil, "", "") {
		assert.NotContains(t, kv, "CAMUNDA_PHYSICALTENANTS_")
	}
}

func TestWithTenantEnvOnlyAddsToCamunda(t *testing.T) {
	env := withTenantEnv([]string{"A=1"}, map[string]string{"CAMUNDA_PHYSICALTENANTS_X_Y": "z"})
	assert.Equal(t, []string{"A=1", "CAMUNDA_PHYSICALTENANTS_X_Y=z"}, env)
	assert.Equal(t, []string{"A=1"}, withTenantEnv([]string{"A=1"}, nil))
}

func TestConnectorsEnvKeepsOnlyOwnTenantSecrets(t *testing.T) {
	env := []string{"MYSECRET_DEFAULT_A=1", "MYSECRET_SALES_B=2", "PATH=/bin"}

	assert.Equal(t, []string{"MYSECRET_DEFAULT_A=1", "PATH=/bin"}, connectorsEnv(env, "MYSECRET_", "MYSECRET_DEFAULT_"))
	assert.Equal(t, []string{"MYSECRET_SALES_B=2", "PATH=/bin"}, connectorsEnv(env, "MYSECRET_", "MYSECRET_SALES_"))
}
