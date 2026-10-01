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
	"os"
	"path/filepath"
	"strings"
	"testing"

	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testTenantsCommand(t *testing.T, input string, terminal bool, passwords ...string) (*tenantsCommand, *bytes.Buffer, *bytes.Buffer) {
	t.Helper()
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	t.Setenv("CAMUNDA_VERSION", "")
	out, errOut := &bytes.Buffer{}, &bytes.Buffer{}
	i := 0
	return &tenantsCommand{
		input: strings.NewReader(input), output: out, errorOutput: errOut,
		isTerminal: func() bool { return terminal },
		readPassword: func() ([]byte, error) {
			pw := passwords[i%len(passwords)]
			i++
			return []byte(pw), nil
		},
		port: 8080,
	}, out, errOut
}

func TestTenantsHelp(t *testing.T) {
	cmd, out, _ := testTenantsCommand(t, "", false, "")
	require.NoError(t, cmd.run(t.TempDir(), nil))
	assert.Contains(t, out.String(), "c8run tenants add")
	out.Reset()
	require.NoError(t, cmd.run(t.TempDir(), []string{"add", "--help"}))
	assert.Contains(t, out.String(), "--no-connectors")
	assert.ErrorContains(t, cmd.run(t.TempDir(), []string{"bogus"}), "unsupported tenants operation")
}

func TestTenantsAddListRemove(t *testing.T) {
	base := t.TempDir()
	cmd, out, _ := testTenantsCommand(t, "y\n", true, "")
	require.NoError(t, cmd.run(base, []string{"add", "sales", "hr", "--no-connectors"}))
	assert.Contains(t, out.String(), "Physical tenant sales added.")
	assert.Contains(t, out.String(), "http://localhost:8080/physical-tenants/hr/operate")
	assert.Contains(t, out.String(), "./c8run start")

	out.Reset()
	require.NoError(t, cmd.run(base, []string{"list"}))
	assert.Contains(t, out.String(), "default")
	assert.Contains(t, out.String(), "sales")
	assert.Contains(t, out.String(), "stopped")

	out.Reset()
	require.NoError(t, cmd.run(base, []string{"remove", "hr"}))
	assert.Contains(t, out.String(), "Removed physical tenant(s): hr.")
	assert.Contains(t, out.String(), "restores access")
}

func TestTenantsAddRejectsInvalidAndDuplicate(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	assert.ErrorContains(t, cmd.run(base, []string{"add", "Sales-EU"}), `try "saleseu"`)
	assert.ErrorContains(t, cmd.run(base, []string{"add", "default"}), "reserved")
	assert.ErrorContains(t, cmd.run(base, []string{"add", "a", "--password", "x"}), "shell history")
	require.NoError(t, cmd.run(base, []string{"add", "a"}))
	assert.ErrorContains(t, cmd.run(base, []string{"add", "a"}), "already exists")
}

func TestTenantsAddWithUserPromptsAndConfirms(t *testing.T) {
	base := t.TempDir()
	cmd, out, errOut := testTenantsCommand(t, "", true, "one", "two", "good", "good")
	require.NoError(t, cmd.run(base, []string{"add", "hr", "--username", "alice"}))
	assert.Contains(t, errOut.String(), "Passwords do not match")
	assert.NotContains(t, out.String()+errOut.String(), "good")
	path, _ := pt.ResolvePath(base)
	pw, ok, err := pt.NewStore(path).Password("hr")
	require.NoError(t, err)
	assert.True(t, ok)
	assert.Equal(t, "good", pw)
}

func TestTenantsAddPasswordStdin(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "fromstdin\n", false, "")
	require.NoError(t, cmd.run(base, []string{"add", "hr", "--username", "alice", "--password-stdin"}))
	path, _ := pt.ResolvePath(base)
	pw, _, _ := pt.NewStore(path).Password("hr")
	assert.Equal(t, "fromstdin", pw)

	assert.ErrorContains(t, cmd.run(base, []string{"add", "x", "--username", "bob"}), "requires a terminal")
}

func TestTenantsRemoveNonInteractiveNeedsYes(t *testing.T) {
	base := t.TempDir()
	cmd, out, errOut := testTenantsCommand(t, "", false, "")
	require.NoError(t, cmd.run(base, []string{"add", "a"}))
	out.Reset()
	require.NoError(t, cmd.run(base, []string{"remove", "a"}))
	assert.Contains(t, errOut.String(), "requires --yes")
	assert.Contains(t, out.String(), "No tenants removed.")
	require.NoError(t, cmd.run(base, []string{"remove", "a", "--yes"}))
	assert.ErrorContains(t, cmd.run(base, []string{"remove", "default", "--yes"}), "cannot be removed")
}

func TestTenantsWarnsWhenRunning(t *testing.T) {
	base := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(base, "camunda.process"), []byte("1"), 0o644))
	cmd, out, errOut := testTenantsCommand(t, "", false, "")
	require.NoError(t, cmd.run(base, []string{"add", "a"}))
	assert.Contains(t, errOut.String(), "next start")
	out.Reset()
	require.NoError(t, cmd.run(base, []string{"list"}))
	assert.Contains(t, out.String(), "pending restart")
}

func TestTenantsVersionGate(t *testing.T) {
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	t.Setenv("CAMUNDA_VERSION", "8.9.0")
	assert.ErrorContains(t, cmd.run(t.TempDir(), []string{"add", "a"}), "8.10 or newer")
}

func TestTenantsExternalMode(t *testing.T) {
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	t.Setenv(pt.ModeEnv, "external")
	assert.ErrorContains(t, cmd.run(t.TempDir(), []string{"list"}), "external")
}

func TestApplyPhysicalTenantsFromFlag(t *testing.T) {
	base := t.TempDir()
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	settings := types.C8RunSettings{Username: "demo", Password: "demo", DisableConnectors: true, PhysicalTenantsFlag: []string{"a,b"}, SecondaryStorageType: "rdbms"}
	require.NoError(t, applyPhysicalTenants(base, "8.10.0", &settings))
	assert.Len(t, settings.PhysicalTenants, 2)
	assert.Equal(t, filepath.Join(base, "configuration", pt.GeneratedConfigName), settings.PhysicalTenantsConfigPath)
	assert.Equal(t, "demo", os.Getenv("CAMUNDA_PHYSICALTENANTS_A_SECURITY_INITIALIZATION_USERS_0_USERNAME"))
	t.Cleanup(func() {
		for key := range pt.CredentialEnv(settings.PhysicalTenants) {
			_ = os.Unsetenv(key)
		}
	})

	old := types.C8RunSettings{PhysicalTenantsFlag: []string{"a"}}
	assert.ErrorContains(t, applyPhysicalTenants(base, "8.9.1", &old), "8.10 or newer")
}
