/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestEnvBasePrefixIsRequired(t *testing.T) {
	t.Setenv(EnvPrefixEnv, "  ")

	_, err := EnvBasePrefix()

	assert.ErrorContains(t, err, EnvPrefixEnv)
}

func TestEnvBasePrefixRejectsInvalidCharacters(t *testing.T) {
	t.Setenv(EnvPrefixEnv, "MY-SECRET_")

	_, err := EnvBasePrefix()

	assert.ErrorContains(t, err, "letters, digits, and underscores")
}

func TestEnvBasePrefixAlwaysEndsWithUnderscore(t *testing.T) {
	t.Setenv(EnvPrefixEnv, "MYSECRET")

	base, err := EnvBasePrefix()

	require.NoError(t, err)
	assert.Equal(t, "MYSECRET_", base)
}

func TestTenantPrefixesAreDisjoint(t *testing.T) {
	assert.Equal(t, "MYSECRET_DEFAULT_", DefaultTenantEnvPrefix("MYSECRET_"))
	assert.Equal(t, "MYSECRET_SALES_", TenantEnvPrefix("MYSECRET_", "sales"))
	// "sales" and "sales2" must not share a prefix, or one tenant could read the other's secrets.
	assert.NotRegexp(t, "^MYSECRET_SALES_", TenantEnvPrefix("MYSECRET_", "sales2"))
}

func TestValidateEnvPrefixRejectsReservedPrefixes(t *testing.T) {
	assert.ErrorContains(t, ValidateEnvPrefix("camunda_DEFAULT_"), `"CAMUNDA_"`)
	assert.ErrorContains(t, ValidateEnvPrefix("SPRING_X_"), `"SPRING_"`)
	assert.NoError(t, ValidateEnvPrefix("MYSECRET_DEFAULT_"))
}

func TestScrubEnvSecretsKeepsOnlyOwnPrefix(t *testing.T) {
	env := []string{"MYSECRET_DEFAULT_A=1", "mysecret_sales_b=2", "MYSECRET_HR_C=3", "PATH=/bin"}

	assert.Equal(t, []string{"mysecret_sales_b=2", "PATH=/bin"}, ScrubEnvSecrets(env, "MYSECRET_", "MYSECRET_SALES_"))
	assert.Equal(t, env, ScrubEnvSecrets(env, "", ""))
}
