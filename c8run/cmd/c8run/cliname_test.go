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
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestWithCLINameRewritesRunnableCommands(t *testing.T) {
	in := "Run `./c8run stop && ./c8run start`. Add one with `c8run tenants add <id>` or `c8run pt list`; see `c8run help`."
	assert.Equal(t,
		"Run `c8ctl cluster stop && c8ctl cluster start`. Add one with `c8ctl cluster tenants add <id>` or `c8ctl cluster pt list`; see `c8ctl cluster help`.",
		withCLIName(in, "c8ctl cluster"))
	// Unrelated mentions of c8run (product name, file names) stay intact.
	assert.Equal(t, "the c8run .env file", withCLIName("the c8run .env file", "c8ctl cluster"))
	assert.Equal(t, in, withCLIName(in, ""), "unset name must keep c8run output unchanged")
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
