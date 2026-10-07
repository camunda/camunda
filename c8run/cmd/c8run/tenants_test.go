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
	"runtime"
	"strings"
	"testing"

	"github.com/camunda/camunda/c8run/internal/overrides"
	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/camunda/camunda/c8run/internal/springconfig"
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
		port:        8080,
		storageType: func(string) string { return "rdbms" },
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
	assert.Contains(t, out.String(), "including the users")
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
	_, passwords, err := pt.NewStore(path).Snapshot()
	require.NoError(t, err)
	assert.Equal(t, "good", passwords["hr"])
}

func TestTenantsAddPasswordStdin(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "fromstdin\n", false, "")
	require.NoError(t, cmd.run(base, []string{"add", "hr", "--username", "alice", "--password-stdin"}))
	path, _ := pt.ResolvePath(base)
	_, passwords, _ := pt.NewStore(path).Snapshot()
	assert.Equal(t, "fromstdin", passwords["hr"])

	assert.ErrorContains(t, cmd.run(base, []string{"add", "x", "--username", "bob"}), "requires a terminal")
}

func TestTenantsRemoveNonInteractiveNeedsYes(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	require.NoError(t, cmd.run(base, []string{"add", "a"}))
	assert.ErrorContains(t, cmd.run(base, []string{"remove", "a"}), "requires --yes")
	assert.ErrorContains(t, cmd.run(base, []string{"reset"}), "requires --yes")
	path, _ := pt.ResolvePath(base)
	saved, err := pt.NewStore(path).List()
	require.NoError(t, err)
	assert.Len(t, saved, 1, "a refused confirmation must not remove anything")
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
	settings := types.C8RunSettings{Port: 8080, Username: "demo", Password: "demo", DisableConnectors: true, PhysicalTenantsFlag: []string{"a,b"}, SecondaryStorageType: "rdbms"}
	require.NoError(t, applyPhysicalTenants(base, "8.10.0", &settings))
	assert.Len(t, settings.PhysicalTenants, 2)
	assert.Equal(t, filepath.Join(base, "configuration", pt.GeneratedConfigName), settings.PhysicalTenantsConfigPath)
	assert.NoFileExists(t, settings.PhysicalTenantsConfigPath, "nothing is written until startup passes the port check")
	assert.Contains(t, string(settings.PhysicalTenantsConfig), "c8run-port")
	assert.Equal(t, "demo", settings.PhysicalTenantsEnv["CAMUNDA_PHYSICALTENANTS_A_SECURITY_INITIALIZATION_USERS_0_USERNAME"])
	_, exported := os.LookupEnv("CAMUNDA_PHYSICALTENANTS_A_SECURITY_INITIALIZATION_USERS_0_USERNAME")
	assert.False(t, exported, "tenant logins must not leak into c8run's own environment")

	old := types.C8RunSettings{PhysicalTenantsFlag: []string{"a"}}
	assert.ErrorContains(t, applyPhysicalTenants(base, "8.9.1", &old), "8.10 or newer")
}

func TestSecretsTenantFlagUsesTenantDirectory(t *testing.T) {
	baseDir := t.TempDir()
	command, output, _ := testSecretsCommand("tenant-value\n", false)
	require.NoError(t, command.run(baseDir, []string{"--tenant", "sales", "set", "API_KEY", "--stdin"}))
	assert.Contains(t, output.String(), "for physical tenant sales")

	tenantDir, err := localsecrets.TenantDirectory(baseDir, "sales")
	require.NoError(t, err)
	content, err := os.ReadFile(filepath.Join(tenantDir, "API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, "tenant-value", string(content))
	_, err = os.Stat(filepath.Join(baseDir, "secrets", "API_KEY"))
	assert.True(t, os.IsNotExist(err), "tenant secret must not land in the default store")

	_, _, err = extractTenantArgument([]string{"--tenant", "Bad-Id", "list"})
	assert.ErrorContains(t, err, "lowercase")
	_, _, err = extractTenantArgument([]string{"--tenant=a", "--tenant=b"})
	assert.ErrorContains(t, err, "only be specified once")
}

func TestConfigureTenantSecretStores(t *testing.T) {
	baseDir := t.TempDir()
	settings := types.C8RunSettings{PhysicalTenants: []types.PhysicalTenant{{ID: "a"}, {ID: "b"}}}
	require.NoError(t, configureTenantSecretStores(baseDir, &settings))
	a := settings.PhysicalTenantsEnv[pt.SecretStoreEnv("a")]
	b := settings.PhysicalTenantsEnv[pt.SecretStoreEnv("b")]
	assert.DirExists(t, a)
	assert.NotEqual(t, a, b)
	assert.NotEqual(t, filepath.Join(baseDir, "secrets"), a)
}

func TestApplyPhysicalTenantsUsesEffectiveStorageType(t *testing.T) {
	base := t.TempDir()
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	t.Setenv("JAVA_OPTS", "")
	t.Setenv("JDK_JAVA_OPTIONS", "")
	t.Setenv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "elasticsearch")
	require.NoError(t, os.MkdirAll(filepath.Join(base, "configuration"), 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(base, "configuration", "application.yaml"), []byte("camunda.data.secondary-storage.type: rdbms\n"), 0o644))
	settings := types.C8RunSettings{DisableConnectors: true, PhysicalTenantsFlag: []string{"a"}}
	applyConfigSettings(base, &settings)
	assert.Equal(t, "elasticsearch", settings.SecondaryStorageType, "driver checks and cleanup see the effective type too")
	require.NoError(t, applyPhysicalTenants(base, "8.10.0", &settings))
	content := settings.PhysicalTenantsConfig
	assert.Contains(t, string(content), "index-prefix: a")
	assert.NotContains(t, string(content), "rdbms")
}

func TestEnvDeclaresTenants(t *testing.T) {
	t.Setenv("JAVA_OPTS", "")
	assert.False(t, envDeclaresTenants())
	t.Setenv("CAMUNDA_PHYSICALTENANTS_X_DATA_FOO", "1")
	assert.True(t, envDeclaresTenants())
}

func TestReservedPortsIncludeCamundaPort(t *testing.T) {
	reserved := reservedPorts(8087, 9086)
	assert.True(t, reserved[8087])
	assert.True(t, reserved[9086])
	assert.False(t, reserved[8086], "8086 is free when Connectors runs on another port")
	assert.True(t, reserved[26500])
}

func TestApplyConfigSettingsDetectsOIDC(t *testing.T) {
	t.Setenv("JAVA_OPTS", "")
	t.Setenv("JDK_JAVA_OPTIONS", "")
	t.Setenv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "")
	base := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(base, "app.yaml"), []byte("camunda:\n  security:\n    authentication:\n      method: oidc\n"), 0o644))
	settings := types.C8RunSettings{Config: "app.yaml"}
	applyConfigSettings(base, &settings)
	assert.True(t, settings.OIDC)
	settings = types.C8RunSettings{}
	applyConfigSettings(base, &settings)
	assert.False(t, settings.OIDC)
	t.Setenv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "basic")
	settings = types.C8RunSettings{Config: "app.yaml"}
	applyConfigSettings(base, &settings)
	assert.False(t, settings.OIDC, "the environment beats config files")
}

func TestApplyPhysicalTenantsFailsWhenSavedTenantsCannotBeFound(t *testing.T) {
	t.Setenv(pt.FileEnv, "")
	if runtime.GOOS == "windows" {
		t.Setenv("APPDATA", "")
	} else {
		t.Setenv("HOME", "")
		t.Setenv("XDG_CONFIG_HOME", "")
	}
	settings := types.C8RunSettings{DisableConnectors: true}
	err := applyPhysicalTenants(t.TempDir(), "8.10.0", &settings)
	if err == nil {
		t.Skip("the platform still resolves a user config directory")
	}
	assert.ErrorContains(t, err, "cannot find your saved physical tenants")
	assert.ErrorContains(t, err, pt.FileEnv)
}

func TestResolveConfigPathsIncludesEveryStandardFileInADirectory(t *testing.T) {
	base := t.TempDir()
	dir := filepath.Join(base, "cfg")
	require.NoError(t, os.MkdirAll(dir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "application.yml"), []byte("camunda:\n  physical-tenants:\n    x: {}\n"), 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "application.properties"), []byte("camunda.physical-tenants.y.foo=1\n"), 0o644))
	paths := springconfig.Paths(base, "cfg")
	assert.Contains(t, paths, filepath.Join(dir, "application.yml"))
	assert.Contains(t, paths, filepath.Join(dir, "application.properties"))
	assert.True(t, pt.ConfigDeclaresTenants(filepath.Join(dir, "application.yml")))
	assert.True(t, pt.ConfigDeclaresTenants(filepath.Join(dir, "application.properties")))
}

func TestStartRejectsExplicitlyEmptyPhysicalTenantsFlag(t *testing.T) {
	original := os.Args
	t.Cleanup(func() { os.Args = original })
	for _, value := range []string{"--physical-tenants=,", "--physical-tenants= "} {
		os.Args = []string{"c8run", "start", value}
		_, _, err := getBaseCommandSettings("start")
		assert.ErrorContains(t, err, "needs at least one tenant ID", value)
	}
}

func TestConfigDirectoryFollowsSpringPrecedence(t *testing.T) {
	base := t.TempDir()
	dir := filepath.Join(base, "cfg")
	require.NoError(t, os.MkdirAll(dir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "application.yml"),
		[]byte("camunda:\n  security:\n    authentication:\n      unprotected-api: true\n  data:\n    secondary-storage:\n      type: rdbms\n"), 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "application.properties"),
		[]byte("camunda.security.authentication.unprotected-api=false\ncamunda.data.secondary-storage.type=elasticsearch\n"), 0o644))
	for _, key := range []string{"JAVA_OPTS", "JDK_JAVA_OPTIONS", "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED",
		"CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API",
		"CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "CAMUNDA_DATA_SECONDARY_STORAGE_TYPE"} {
		t.Setenv(key, "")
	}

	paths := springconfig.Paths(base, "cfg")
	assert.Equal(t, filepath.Join(dir, "application.properties"), paths[0], ".properties wins over YAML in one location")
	assert.True(t, overrides.ConnectorsAuthRequired(paths), "the protecting .properties value must win, so connectors get credentials")

	settings := types.C8RunSettings{Config: "cfg"}
	applyConfigSettings(base, &settings)
	assert.Equal(t, "elasticsearch", settings.SecondaryStorageType, "tenant isolation must follow the .properties storage type")
}

func TestExternalTenantConfigDoesNotNeedTheSavedTenantsFile(t *testing.T) {
	t.Setenv(pt.FileEnv, "")
	t.Setenv("JAVA_OPTS", "")
	if runtime.GOOS == "windows" {
		t.Setenv("APPDATA", "")
	} else {
		t.Setenv("HOME", "")
		t.Setenv("XDG_CONFIG_HOME", "")
	}
	cfg := filepath.Join(t.TempDir(), "app.yaml")
	require.NoError(t, os.WriteFile(cfg, []byte("camunda:\n  physical-tenants:\n    x: {}\n"), 0o644))
	settings := types.C8RunSettings{DisableConnectors: true, ConfigPaths: []string{cfg}}
	require.NoError(t, applyPhysicalTenants(t.TempDir(), "8.10.0", &settings))
	assert.Empty(t, settings.PhysicalTenants)

	t.Setenv(pt.ModeEnv, "external")
	settings = types.C8RunSettings{DisableConnectors: true}
	require.NoError(t, applyPhysicalTenants(t.TempDir(), "8.10.0", &settings))
}

func TestTenantsAddRejectsIDsTooLongForRDBMS(t *testing.T) {
	base := t.TempDir()
	cmd, _, _ := testTenantsCommand(t, "", false, "")
	assert.ErrorContains(t, cmd.run(base, []string{"add", "salesemea1"}), `at most 8 characters (try "saleseme")`)
	tenants, err := pt.NewStore(os.Getenv(pt.FileEnv)).List()
	require.NoError(t, err)
	assert.Empty(t, tenants, "a rejected id must not be saved")
	require.NoError(t, cmd.run(base, []string{"add", "saleseme"}))

	cmd.storageType = func(string) string { return "elasticsearch" }
	require.NoError(t, cmd.run(base, []string{"add", "salesemea1"}))
}

func TestApplyPhysicalTenantsRejectsIDsTooLongForRDBMS(t *testing.T) {
	base := t.TempDir()
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	settings := types.C8RunSettings{DisableConnectors: true, PhysicalTenantsFlag: []string{"salesemea1"}, SecondaryStorageType: "rdbms"}
	assert.ErrorContains(t, applyPhysicalTenants(base, "8.10.0", &settings), "c8run tenants remove salesemea1")

	es := types.C8RunSettings{DisableConnectors: true, PhysicalTenantsFlag: []string{"salesemea1"}, SecondaryStorageType: "elasticsearch"}
	require.NoError(t, applyPhysicalTenants(base, "8.10.0", &es))
}

func TestFlatYAMLStorageTypeIsolatesTenantsByIndexPrefix(t *testing.T) {
	base := t.TempDir()
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	t.Setenv("JAVA_OPTS", "")
	t.Setenv("JDK_JAVA_OPTIONS", "")
	t.Setenv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "")
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", "")
	cfg := filepath.Join(base, "user.yaml")
	require.NoError(t, os.WriteFile(cfg, []byte("camunda.data.secondary-storage.type: elasticsearch\n"), 0o644))

	settings := types.C8RunSettings{Config: "user.yaml", DisableConnectors: true, PhysicalTenantsFlag: []string{"sales"}}
	applyConfigSettings(base, &settings)
	require.NoError(t, applyPhysicalTenants(base, "8.10.0", &settings))

	content := string(settings.PhysicalTenantsConfig)
	assert.Contains(t, content, "index-prefix: sales")
	assert.NotContains(t, content, "rdbms")
}
