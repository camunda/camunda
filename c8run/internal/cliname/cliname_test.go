/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package cliname

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRewriteAs(t *testing.T) {
	for in, want := range map[string]string{
		"`./c8run start`":            "`c8ctl cluster start`",
		"`c8run stop`":               "`c8ctl cluster stop`",
		"`c8run help`":               "`c8ctl cluster help`",
		"`c8run secrets set X`":      "`c8ctl cluster secrets set X`",
		"`c8run physical-tenants`":   "`c8ctl cluster physical-tenants`",
		"the c8run .env file":        "the c8run .env file",
		"c8run startup is complete":  "c8run startup is complete",
		"/opt/c8run/log/camunda.log": "/opt/c8run/log/camunda.log",
	} {
		assert.Equal(t, want, RewriteAs(in, "c8ctl cluster"), in)
		assert.Equal(t, in, RewriteAs(in, ""), "empty name must not rewrite %q", in)
	}
}

func TestRewriteAsUsesNameLiterally(t *testing.T) {
	assert.Equal(t, "`/opt/$TOOLS/c8ctl cluster physical-tenants list`", RewriteAs("`c8run physical-tenants list`", "/opt/$TOOLS/c8ctl cluster"))
	assert.Equal(t, "`x${1}$$y stop`", RewriteAs("`c8run stop`", "x${1}$$y"))
}

func TestNameTrimsWhitespace(t *testing.T) {
	t.Setenv(Env, "  c8ctl cluster \n")
	assert.Equal(t, "c8ctl cluster", Name())
	assert.Equal(t, "`c8ctl cluster stop`", Rewrite("`c8run stop`"))
}

func TestWriter(t *testing.T) {
	var buf bytes.Buffer
	t.Setenv(Env, "")
	assert.Same(t, &buf, Writer(&buf), "unset name returns the writer unchanged")

	t.Setenv(Env, "c8ctl cluster")
	msg := "run `c8run stop`\n"
	n, err := Writer(&buf).Write([]byte(msg))
	require.NoError(t, err)
	assert.Equal(t, len(msg), n, "Write reports the input length, not the rewritten length")
	assert.Equal(t, "run `c8ctl cluster stop`\n", buf.String())
}
