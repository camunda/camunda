/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// C8RUN_E2E_DIR must be a dedicated, stopped distribution containing the binary under test.
// The test owns start/stop and leaves its unique tenants' engine data in that distribution.
func TestPhysicalTenantSelectionE2E(t *testing.T) {
	dir := os.Getenv("C8RUN_E2E_DIR")
	if dir == "" {
		t.Skip("set C8RUN_E2E_DIR to a dedicated distribution to run live tenant selection validation")
	}
	dir, err := filepath.Abs(dir)
	require.NoError(t, err)
	binary := filepath.Join(dir, "c8run")
	if runtime.GOOS == "windows" {
		binary += ".exe"
	}
	require.FileExists(t, binary)
	require.NoFileExists(t, filepath.Join(dir, "camunda.process"))
	state := t.TempDir()
	t.Setenv("C8RUN_TENANTS_FILE", filepath.Join(state, "tenants.yaml"))
	t.Setenv("C8RUN_SECRETS_DIR", filepath.Join(state, "secrets"))
	t.Setenv("C8RUN_TENANTS_MODE", "local")
	t.Setenv("C8RUN_SECRETS_MODE", "local")
	t.Setenv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false")
	t.Setenv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "true")
	suffix := fmt.Sprintf("%x", time.Now().UnixNano())[9:]
	existing, fresh, unsaved := "a"+suffix, "b"+suffix, "c"+suffix
	password := "local-selection-test"
	run := func(input string, args ...string) string {
		t.Helper()
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
		defer cancel()
		cmd := exec.CommandContext(ctx, binary, args...)
		cmd.Dir = dir
		cmd.Stdin = strings.NewReader(input)
		output, err := cmd.CombinedOutput()
		require.NoError(t, err, "%v\n%s", args, output)
		t.Logf("c8run %v\n%s", args, output)
		return string(output)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
		defer cancel()
		cmd := exec.CommandContext(ctx, binary, "stop")
		cmd.Dir = dir
		output, err := cmd.CombinedOutput()
		assert.NoError(t, err, "%s", output)
	})
	add := func(id string) {
		run(password+"\n", "physical-tenants", "add", id, "--username", "alice", "--password-stdin", "--no-connectors")
	}
	check := func(id, user, pass string, status int) {
		t.Helper()
		request, err := http.NewRequest(http.MethodPost, "http://localhost:8080/physical-tenants/"+id+"/v2/process-definitions/search", strings.NewReader("{}"))
		require.NoError(t, err)
		request.SetBasicAuth(user, pass)
		request.Header.Set("Content-Type", "application/json")
		client := http.Client{Timeout: 10 * time.Second}
		response, err := client.Do(request)
		require.NoError(t, err)
		defer response.Body.Close()
		require.Equal(t, status, response.StatusCode, "tenant=%s login=%s", id, user)
	}
	checkSaved := func(id string) {
		check(id, "alice", password, http.StatusOK)
		check(id, "demo", "demo", http.StatusUnauthorized)
		assert.NoFileExists(t, filepath.Join(dir, "connectors-"+id+".process"))
	}

	add(existing)
	run("", "start", "--no-browser")
	checkSaved(existing)
	run("", "stop")
	add(fresh)
	before, err := os.ReadFile(os.Getenv("C8RUN_TENANTS_FILE"))
	require.NoError(t, err)
	run("", "start", "--no-browser", "--physical-tenants", existing+","+fresh, "--physical-tenants", unsaved)
	checkSaved(existing)
	checkSaved(fresh)
	check(unsaved, "demo", "demo", http.StatusOK)
	assert.FileExists(t, filepath.Join(dir, "connectors-"+unsaved+".process"))
	listed := run("", "physical-tenants", "list")
	for _, id := range []string{existing, fresh} {
		assert.Regexp(t, id+`\s+active\s+alice\s+no`, listed)
	}
	assert.Regexp(t, unsaved+`\s+active \(not saved\)\s+demo\s+yes`, listed)
	after, err := os.ReadFile(os.Getenv("C8RUN_TENANTS_FILE"))
	require.NoError(t, err)
	assert.Equal(t, before, after)
	run("", "stop")
	run("", "start", "--no-browser")
	checkSaved(existing)
	checkSaved(fresh)
	check(unsaved, "demo", "demo", http.StatusNotFound)
}
