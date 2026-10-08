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
	"testing"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestConfigureEnvSecretStoresGivesEachTenantItsOwnPrefix(t *testing.T) {
	t.Setenv("C8RUN_SECRETS_ENV_PREFIX", "MYSECRET")
	t.Setenv(RootEnvSecretStorePrefixEnv, "")
	settings := types.C8RunSettings{PhysicalTenants: []types.PhysicalTenant{{ID: "sales"}, {ID: "hr"}}}

	require.NoError(t, configureEnvSecretStores(&settings))

	assert.Equal(t, "MYSECRET_DEFAULT_", os.Getenv(RootEnvSecretStorePrefixEnv))
	assert.Equal(t, map[string]string{
		"CAMUNDA_PHYSICALTENANTS_SALES_SECRETS_STORES_ENV_DEFAULT_PREFIX": "MYSECRET_SALES_",
		"CAMUNDA_PHYSICALTENANTS_HR_SECRETS_STORES_ENV_DEFAULT_PREFIX":    "MYSECRET_HR_",
	}, settings.PhysicalTenantsEnv)
	assert.Equal(t, "MYSECRET_", settings.SecretsEnvPrefix)
}

func TestConfigureEnvSecretStoresRefusesReservedPrefix(t *testing.T) {
	t.Setenv("C8RUN_SECRETS_ENV_PREFIX", "CAMUNDA_")
	t.Setenv(RootEnvSecretStorePrefixEnv, "")
	settings := types.C8RunSettings{}

	err := configureEnvSecretStores(&settings)

	assert.ErrorContains(t, err, `"CAMUNDA_"`)
	assert.Empty(t, os.Getenv(RootEnvSecretStorePrefixEnv))
	assert.Empty(t, settings.SecretsEnvPrefix)
}

func TestConfigureEnvSecretStoresRequiresPrefix(t *testing.T) {
	t.Setenv("C8RUN_SECRETS_ENV_PREFIX", "")
	settings := types.C8RunSettings{}

	assert.ErrorContains(t, configureEnvSecretStores(&settings), "C8RUN_SECRETS_ENV_PREFIX")
}
