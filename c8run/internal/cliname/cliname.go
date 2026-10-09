/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Package cliname rewrites c8run command hints for wrappers such as c8ctl
// ("c8ctl cluster"), so printed commands can be run as shown.
package cliname

import (
	"io"
	"os"
	"regexp"
	"strings"
)

// Env names the variable a wrapper sets to its own command prefix.
const Env = "C8RUN_CLI_NAME"

// commandPattern matches c8run invocations in user-facing text: the launcher
// form (`./c8run stop`) and subcommand references (`c8run tenants list`).
// The pt/physical-tenants aliases are not rewritten because wrappers only
// delegate the canonical subcommands.
var commandPattern = regexp.MustCompile(`(?:\./)?c8run (start|stop|help|tenants|secrets)\b`)

// Name returns the configured wrapper name, or "" when c8run runs directly.
func Name() string {
	return strings.TrimSpace(os.Getenv(Env))
}

// RewriteAs rewrites c8run invocations in s to use name. An empty name
// returns s unchanged.
func RewriteAs(s, name string) string {
	if name == "" {
		return s
	}
	return commandPattern.ReplaceAllString(s, strings.ReplaceAll(name, "$", "$$")+" $1")
}

// Rewrite applies the configured wrapper name to s.
func Rewrite(s string) string {
	return RewriteAs(s, Name())
}

type writer struct {
	w    io.Writer
	name string
}

// Write rewrites each call independently; c8run writes each message in one
// call, so a command name never spans two writes. Write only command-bearing
// messages through it: dynamic values such as paths must bypass it.
func (c writer) Write(p []byte) (int, error) {
	if _, err := io.WriteString(c.w, RewriteAs(string(p), c.name)); err != nil {
		return 0, err
	}
	return len(p), nil
}

// Writer wraps w so command hints use the configured wrapper name, and
// returns w unchanged when none is set.
func Writer(w io.Writer) io.Writer {
	if name := Name(); name != "" {
		return writer{w: w, name: name}
	}
	return w
}
