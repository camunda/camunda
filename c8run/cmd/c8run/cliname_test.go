/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"testing"

	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestWithCLINameRewritesRunnableCommands(t *testing.T) {
	in := "Run `./c8run stop && ./c8run start`. Add one with `c8run tenants add <id>` or `c8run tenants list`; see `c8run help`."
	assert.Equal(t,
		"Run `c8ctl cluster stop && c8ctl cluster start`. Add one with `c8ctl cluster tenants add <id>` or `c8ctl cluster tenants list`; see `c8ctl cluster help`.",
		withCLIName(in, "c8ctl cluster"))
	// Unrelated mentions of c8run (product name, file names) stay intact.
	assert.Equal(t, "the c8run .env file", withCLIName("the c8run .env file", "c8ctl cluster"))
	assert.Equal(t, in, withCLIName(in, ""), "unset name must keep c8run output unchanged")
	// Wrappers delegate only canonical subcommands, so aliases are left alone.
	assert.Equal(t, "`c8run pt list`", withCLIName("`c8run pt list`", "c8ctl cluster"))
}

func TestTenantsHintsUseConfiguredCLIName(t *testing.T) {
	t.Setenv(cliNameEnv, "c8ctl cluster")
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	var out bytes.Buffer
	cmd.output = brandWriter(&out)
	require.NoError(t, cmd.run(base, []string{"list"}))
	assert.Contains(t, out.String(), "`c8ctl cluster tenants add <id>`")
	assert.NotContains(t, out.String(), "c8run tenants")
}

func TestUsageTextUsesConfiguredCLIName(t *testing.T) {
	help := usageText("/opt/c8run/c8run", "c8ctl cluster")
	assert.Contains(t, help, "c8ctl cluster tenants add sales")
	assert.NotContains(t, help, "/opt/c8run/c8run")
	assert.Contains(t, usageText("./c8run", ""), "./c8run stop", "unset name keeps the executable path")
}

func TestConfirmReadFailureIsAnError(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "", true, "")
	require.NoError(t, cmd.run(base, []string{"add", "a"}))
	cmd.input = failingReader{}
	for _, args := range [][]string{{"remove", "a"}, {"reset"}} {
		assert.ErrorContains(t, cmd.run(base, args), "read confirmation: test read failure")
	}
}

func TestTenantsHelpHidesAliasesForWrapper(t *testing.T) {
	cmd, out, _ := testTenantsCommand(t, "", false, "")
	require.NoError(t, cmd.run(t.TempDir(), []string{"help"}))
	assert.Contains(t, out.String(), "without saving them.\n\nAliases: c8run pt, c8run physical-tenants.\nC8RUN_TENANTS_FILE")

	t.Setenv(cliNameEnv, "c8ctl cluster")
	cmd, out, _ = testTenantsCommand(t, "", false, "")
	cmd.output = brandWriter(out)
	require.NoError(t, cmd.run(t.TempDir(), []string{"help"}))
	assert.NotContains(t, out.String(), "Aliases")
	assert.NotContains(t, out.String(), " pt")
}

func TestPathOutputIsNotRewritten(t *testing.T) {
	t.Setenv(cliNameEnv, "c8ctl cluster")
	dir := filepath.Join(t.TempDir(), "c8run secrets")
	t.Setenv("C8RUN_SECRETS_DIR", dir)
	cmd, _, _ := testSecretsCommand("", false)
	var branded, plain bytes.Buffer
	cmd.output, cmd.plainOutput = brandWriter(&branded), &plain
	require.NoError(t, cmd.run(t.TempDir(), []string{"path"}))
	assert.Contains(t, plain.String(), dir)
	assert.Empty(t, branded.String())

	tenantsFile := filepath.Join(t.TempDir(), "c8run tenants", "t.yaml")
	tcmd, _, _ := testTenantsCommand(t, "", false, "")
	t.Setenv(pt.FileEnv, tenantsFile)
	plain.Reset()
	tcmd.output, tcmd.plainOutput = brandWriter(&branded), &plain
	require.NoError(t, tcmd.run(t.TempDir(), []string{"path"}))
	assert.Contains(t, plain.String(), tenantsFile)
}

// failingReader lives in secrets_test.go on main; stable/8.10 predates it.
type failingReader struct{}

func (failingReader) Read([]byte) (int, error) {
	return 0, errors.New("test read failure")
}

func TestPathWarningPrintsDirectoriesVerbatim(t *testing.T) {
	t.Setenv(cliNameEnv, "c8ctl cluster")
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("XDG_CONFIG_HOME", filepath.Join(home, ".config"))
	t.Setenv("APPDATA", filepath.Join(home, "AppData"))
	defaultDir, err := localsecrets.DefaultDirectory()
	require.NoError(t, err)
	require.NoError(t, os.MkdirAll(defaultDir, 0o700))
	require.NoError(t, os.WriteFile(filepath.Join(defaultDir, "OLD_SECRET"), []byte("x"), 0o600))
	dir := filepath.Join(t.TempDir(), "c8run secrets")
	t.Setenv("C8RUN_SECRETS_DIR", dir)

	cmd, _, _ := testSecretsCommand("", false)
	var branded, plain bytes.Buffer
	cmd.errorOutput, cmd.plainErrorOutput = brandWriter(&branded), &plain
	require.NoError(t, cmd.run(t.TempDir(), []string{"list"}))
	assert.Contains(t, plain.String(), "platform-default directory also contains entries")
	assert.Contains(t, plain.String(), "  "+dir+"\n")
	assert.Contains(t, plain.String(), "Run 'c8ctl cluster secrets doctor'")
	assert.Empty(t, branded.String())
}
