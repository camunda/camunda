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
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"testing"

	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type listRequest struct {
	path, username, password string
}

// fakeCluster serves the secrets list endpoint with the given status and body and records the
// last request.
func fakeCluster(t *testing.T, status int, body string) (string, *listRequest) {
	t.Helper()
	t.Setenv(localsecrets.ModeEnv, "env")
	t.Setenv(localsecrets.EnvPrefixEnv, "C8_")
	t.Setenv(pt.FileEnv, filepath.Join(t.TempDir(), pt.FileName))
	last := &listRequest{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		last.path = r.URL.Path
		last.username, last.password, _ = r.BasicAuth()
		w.WriteHeader(status)
		_, _ = fmt.Fprint(w, body)
	}))
	t.Cleanup(server.Close)
	u, err := url.Parse(server.URL)
	require.NoError(t, err)
	return u.Port(), last
}

func TestEnvModeListPrintsTheDefaultTenantsNamesFromTheCluster(t *testing.T) {
	// given
	port, last := fakeCluster(t, http.StatusOK, `{"references":["camunda.secrets.DB_PASSWORD","camunda.secrets.db-user"]}`)
	command, output, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list", "--port", port})

	// then
	require.NoError(t, err)
	assert.Equal(t, "DB_PASSWORD\ndb-user\n", output.String())
	assert.Equal(t, listRequest{"/v2/secrets/list", "demo", "demo"}, *last)
}

func TestEnvModeListUsesThePhysicalTenantsSavedLogin(t *testing.T) {
	// given
	port, last := fakeCluster(t, http.StatusOK, `{"references":["camunda.secrets.HR_KEY"]}`)
	require.NoError(t, pt.NewStore(os.Getenv(pt.FileEnv)).Add([]pt.Tenant{{ID: "tenanta", Username: "alice"}}, map[string]string{"tenanta": "pw"}))
	command, output, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"--physical-tenant", "tenanta", "list", "--port=" + port})

	// then
	require.NoError(t, err)
	assert.Equal(t, "HR_KEY\n", output.String())
	assert.Equal(t, listRequest{"/physical-tenants/tenanta/v2/secrets/list", "alice", "pw"}, *last)
}

func TestEnvModeListPrefersTheGivenLogin(t *testing.T) {
	// given
	port, last := fakeCluster(t, http.StatusOK, `{"references":[]}`)
	require.NoError(t, pt.NewStore(os.Getenv(pt.FileEnv)).Add([]pt.Tenant{{ID: "tenanta", Username: "alice"}}, map[string]string{"tenanta": "pw"}))
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"--physical-tenant", "tenanta", "list", "--port", port, "--username", "bob", "--password=other"})

	// then
	require.NoError(t, err)
	assert.Equal(t, "bob", last.username)
	assert.Equal(t, "other", last.password)
}

func TestEnvModeListPrintsNothingWithoutSecrets(t *testing.T) {
	// given
	port, _ := fakeCluster(t, http.StatusOK, `{"references":[]}`)
	command, output, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list", "--port", port})

	// then
	require.NoError(t, err)
	assert.Empty(t, output.String())
}

func TestEnvModeListNamesTheLoginFlagsWhenRejected(t *testing.T) {
	for _, status := range []int{http.StatusUnauthorized, http.StatusForbidden} {
		t.Run(strconv.Itoa(status), func(t *testing.T) {
			// given
			port, _ := fakeCluster(t, status, "")
			command, _, _ := testSecretsCommand("", false)

			// when
			err := command.run(t.TempDir(), []string{"list", "--port", port})

			// then
			assert.ErrorContains(t, err, "--username and --password")
		})
	}
}

func TestEnvModeListNamesAnUnknownPhysicalTenant(t *testing.T) {
	// given
	port, _ := fakeCluster(t, http.StatusNotFound, "")
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"--physical-tenant", "nope", "list", "--port", port})

	// then
	assert.ErrorContains(t, err, `physical tenant "nope"`)
}

func TestEnvModeListReportsOtherFailures(t *testing.T) {
	// given
	port, _ := fakeCluster(t, http.StatusServiceUnavailable, "")
	command, _, _ := testSecretsCommand("", false)

	// when
	err := command.run(t.TempDir(), []string{"list", "--port", port})

	// then
	assert.ErrorContains(t, err, "HTTP 503")
}

func TestEnvModeListSaysC8RunMustBeRunning(t *testing.T) {
	// given
	t.Setenv(localsecrets.ModeEnv, "env")
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	port := strconv.Itoa(listener.Addr().(*net.TCPAddr).Port)
	require.NoError(t, listener.Close())
	command, _, _ := testSecretsCommand("", false)

	// when
	err = command.run(t.TempDir(), []string{"list", "--port", port})

	// then
	assert.ErrorContains(t, err, "needs c8run running")
}

func TestEnvModeListRejectsInvalidArguments(t *testing.T) {
	t.Setenv(localsecrets.ModeEnv, "env")
	for _, args := range [][]string{{"--port", "x"}, {"--port", "70000"}, {"--port"}, {"--verbose"}, {"DB_PASSWORD"}} {
		t.Run(fmt.Sprint(args), func(t *testing.T) {
			command, _, _ := testSecretsCommand("", false)
			assert.Error(t, command.run(t.TempDir(), append([]string{"list"}, args...)))
		})
	}
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
