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

	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// isolateSecretsEnv registers every variable configureSecretStores writes, so t.Setenv restores
// them when the test ends.
func isolateSecretsEnv(t *testing.T, mode, prefix string) {
	t.Helper()
	t.Setenv(localsecrets.ModeEnv, mode)
	t.Setenv(localsecrets.EnvPrefixEnv, prefix)
	t.Setenv(localsecrets.DirectoryEnv, "")
	t.Setenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH", "")
	t.Setenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX", "")
}

func tenants(ids ...string) []types.PhysicalTenant {
	var result []types.PhysicalTenant
	for _, id := range ids {
		result = append(result, types.PhysicalTenant{ID: id})
	}
	return result
}

func TestEnvSecretsModeConfiguresAnEnvStorePerTenant(t *testing.T) {
	// given
	isolateSecretsEnv(t, "env", "  C8_SECRET_  ")
	settings := types.C8RunSettings{PhysicalTenants: tenants("hr", "sales")}

	// when
	require.NoError(t, configureSecretStores(t.TempDir(), &settings))

	// then
	assert.Equal(t, "C8_SECRET_DEFAULT_", os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
	assert.Equal(t, "C8_SECRET_", settings.SecretsEnvPrefix, "connectors runtimes filter other tenants' variables by it")
	assert.Equal(t, "C8_SECRET_HR_", settings.PhysicalTenantsEnv["CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_ENV_DEFAULT_PREFIX"])
	assert.Equal(t, "C8_SECRET_SALES_", settings.PhysicalTenantsEnv["CAMUNDA_PHYSICALTENANTS_SALES_SECRETS_STORES_ENV_DEFAULT_PREFIX"])
	assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH"), "env mode configures no file store")
	assert.Empty(t, os.Getenv(localsecrets.DirectoryEnv))
	assert.NotContains(t, settings.PhysicalTenantsEnv, "CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_FILE_DEFAULT_PATH")
}

func TestEnvSecretsModeKeepsALowercasePrefixAsWritten(t *testing.T) {
	// given
	isolateSecretsEnv(t, "env", "c8_")
	settings := types.C8RunSettings{PhysicalTenants: tenants("hr")}

	// when
	require.NoError(t, configureSecretStores(t.TempDir(), &settings))

	// then
	assert.Equal(t, "c8_DEFAULT_", os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
	assert.Equal(t, "c8_HR_", settings.PhysicalTenantsEnv["CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_ENV_DEFAULT_PREFIX"])
}

func TestEnvSecretsModeRequiresAWellFormedPrefix(t *testing.T) {
	for _, prefix := range []string{"", "   ", "C8", "C8.", "C8-", "1C8_", "C8-_"} {
		t.Run(prefix, func(t *testing.T) {
			// given
			isolateSecretsEnv(t, "env", prefix)
			settings := types.C8RunSettings{}

			// when
			err := configureSecretStores(t.TempDir(), &settings)

			// then
			assert.ErrorContains(t, err, localsecrets.EnvPrefixEnv)
			assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
		})
	}
}

func TestEnvSecretsModeRefusesTenantsDeclaredOutsideC8Run(t *testing.T) {
	// given
	isolateSecretsEnv(t, "env", "C8_")
	settings := types.C8RunSettings{PhysicalTenantsDeclaredIn: []string{"CAMUNDA_PHYSICALTENANTS_* variables", "--config app.yaml"}}

	// when
	err := configureSecretStores(t.TempDir(), &settings)

	// then
	assert.ErrorContains(t, err, "C8RUN_SECRETS_MODE=env")
	assert.ErrorContains(t, err, "CAMUNDA_PHYSICALTENANTS_* variables and --config app.yaml")
	assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
}

func TestEnvSecretsModeWithoutTenantsConfiguresTheDefaultTenantOnly(t *testing.T) {
	// given
	isolateSecretsEnv(t, "env", "C8_")
	settings := types.C8RunSettings{}

	// when
	require.NoError(t, configureSecretStores(t.TempDir(), &settings))

	// then
	assert.Equal(t, "C8_DEFAULT_", os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
	assert.Empty(t, settings.PhysicalTenantsEnv)
}

func TestExternalSecretsModeRefusesTenantsManagedByC8Run(t *testing.T) {
	// given
	isolateSecretsEnv(t, "external", "")
	settings := types.C8RunSettings{PhysicalTenants: tenants("hr")}

	// when
	err := configureSecretStores(t.TempDir(), &settings)

	// then
	assert.ErrorContains(t, err, "C8RUN_SECRETS_MODE=local or C8RUN_SECRETS_MODE=env")
}

func TestExternalSecretsModeWithoutManagedTenantsConfiguresNothing(t *testing.T) {
	// given
	isolateSecretsEnv(t, "external", "C8_")
	settings := types.C8RunSettings{}

	// when
	require.NoError(t, configureSecretStores(t.TempDir(), &settings))

	// then
	assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH"))
	assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
	assert.Empty(t, settings.PhysicalTenantsEnv)
}

func TestLocalSecretsModeConfiguresFileStoresAndIgnoresThePrefix(t *testing.T) {
	// given
	isolateSecretsEnv(t, "local", "not a prefix")
	directory := filepath.Join(t.TempDir(), "secrets")
	t.Setenv(localsecrets.DirectoryEnv, directory)
	settings := types.C8RunSettings{PhysicalTenants: tenants("hr")}

	// when
	require.NoError(t, configureSecretStores(t.TempDir(), &settings))

	// then
	assert.DirExists(t, directory)
	assert.Empty(t, settings.SecretsEnvPrefix)
	assert.DirExists(t, os.Getenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH"))
	assert.Equal(t, os.Getenv(localsecrets.DirectoryEnv), os.Getenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH"))
	assert.DirExists(t, settings.PhysicalTenantsEnv["CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_FILE_DEFAULT_PATH"])
	assert.Empty(t, os.Getenv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"))
	assert.NotContains(t, settings.PhysicalTenantsEnv, "CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_ENV_DEFAULT_PREFIX")
}

func TestInvalidSecretsModeIsRejected(t *testing.T) {
	// given
	isolateSecretsEnv(t, "vault", "")

	// when
	err := configureSecretStores(t.TempDir(), &types.C8RunSettings{})

	// then
	assert.ErrorContains(t, err, "must be local, external or env")
}
