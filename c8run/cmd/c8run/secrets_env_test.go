/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestEnvModeListPrintsTheDefaultTenantsNamesFromTheEnvironment(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	t.Setenv("C8_DEFAULT_DB_PASSWORD", "s3cr3t")
	t.Setenv("C8_DEFAULT_db-user", "admin")
	t.Setenv("C8_TENANTA_HR_KEY", "hr")
	command, output, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list"})

	// then
	require.NoError(t, err)
	assert.Equal(t, "DB_PASSWORD\ndb-user\n", output.String())
	assert.NotContains(t, output.String(), "s3cr3t")
}

func TestEnvModeListPrintsASavedPhysicalTenantsNames(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	require.NoError(t, pt.NewStore(os.Getenv(pt.FileEnv)).Add([]pt.Tenant{{ID: "tenanta"}}, nil))
	t.Setenv("C8_DEFAULT_DB_PASSWORD", "s3cr3t")
	t.Setenv("C8_TENANTA_HR_KEY", "hr")
	command, output, errorOutput := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"--physical-tenant", "tenanta", "list"})

	// then
	require.NoError(t, err)
	assert.Equal(t, "HR_KEY\n", output.String())
	assert.Empty(t, errorOutput.String())
}

func TestEnvModeListWarnsAboutAnUnknownPhysicalTenant(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	t.Setenv("C8_NOPE_KEY", "x")
	command, output, errorOutput := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"--physical-tenant", "nope", "list"})

	// then
	require.NoError(t, err, "listing stays usable; the warning only explains why c8run resolves nothing")
	assert.Equal(t, "KEY\n", output.String())
	assert.Contains(t, errorOutput.String(), `physical tenant "nope"`)
}

func TestEnvironmentSecretNamesFollowTheEnvStoresRules(t *testing.T) {
	env := []string{
		"PATH=/bin",
		"C8_DEFAULT_B=2",
		"C8_DEFAULT_A=1",
		"C8_DEFAULT_EMPTY=",
		"c8_default_LOWER=x",
		"C8_DEFAULT_has.dot=x",
		"C8_DEFAULT_=x",
		"C8_DEFAULT_WITH_EQUALS=a=b",
	}

	assert.Equal(t, []string{"A", "B", "WITH_EQUALS"}, environmentSecretNames(env, "C8_DEFAULT_"))
}

func TestEnvModeListAsksForThePrefixWhenUnset(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "")
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list"})

	// then
	assert.ErrorContains(t, err, localsecrets.EnvPrefixEnv)
}

func TestEnvModeListRejectsArguments(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list", "--port", "8080"})

	// then
	assert.ErrorContains(t, err, "usage: c8run secrets list")
}

func TestEnvModeWriteCommandsNameTheVariableToSet(t *testing.T) {
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	for _, tc := range []struct {
		args     []string
		variable string
	}{
		{[]string{"set", "DB_PASSWORD", "--stdin"}, "C8_DEFAULT_DB_PASSWORD"},
		{[]string{"delete", "DB_PASSWORD", "--yes"}, "C8_DEFAULT_DB_PASSWORD"},
		{[]string{"import", "secrets.env"}, "C8_DEFAULT_<NAME>"},
		{[]string{"path"}, "C8_DEFAULT_<NAME>"},
		{[]string{"--physical-tenant", "tenanta", "set", "DB_PASSWORD"}, "C8_TENANTA_DB_PASSWORD"},
	} {
		t.Run(fmt.Sprint(tc.args), func(t *testing.T) {
			// given
			baseDir := t.TempDir()
			directory := filepath.Join(t.TempDir(), "secrets")
			t.Setenv(localsecrets.DirectoryEnv, directory)
			command, _, _ := testSecretsCommand("value\n", false)

			// when
			err := command.run(baseDir, tc.args)

			// then
			assert.ErrorContains(t, err, tc.variable)
			assert.NoDirExists(t, directory, "env mode writes nothing")
		})
	}
}

func TestEnvModeWriteCommandsAskForThePrefixWhenUnset(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "")
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"set", "DB_PASSWORD"})

	// then
	assert.ErrorContains(t, err, "Set C8RUN_SECRETS_ENV_PREFIX")
}
