/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"io"
	"os"
	"regexp"
	"strings"
)

// cliNameEnv lets a wrapper such as c8ctl ("c8ctl cluster") replace the
// c8run command names in hints, so printed commands can be run as shown.
const cliNameEnv = "C8RUN_CLI_NAME"

// c8runCommandPattern matches c8run invocations in user-facing text: the
// launcher form (`./c8run stop`) and subcommand references (`c8run tenants list`).
var c8runCommandPattern = regexp.MustCompile(`(?:\./)?c8run (start|stop|help|tenants|secrets|pt|physical-tenants)\b`)

// withCLIName rewrites c8run invocations in s to use name. An empty name
// returns s unchanged.
func withCLIName(s, name string) string {
	if name == "" {
		return s
	}
	return c8runCommandPattern.ReplaceAllString(s, name+" $1")
}

func cliName() string {
	return strings.TrimSpace(os.Getenv(cliNameEnv))
}

// cliNameWriter applies withCLIName to every write. c8run writes each
// message in one call, so a command name never spans two writes.
type cliNameWriter struct {
	w    io.Writer
	name string
}

func (c cliNameWriter) Write(p []byte) (int, error) {
	if _, err := io.WriteString(c.w, withCLIName(string(p), c.name)); err != nil {
		return 0, err
	}
	return len(p), nil
}

// brandWriter wraps w with cliNameWriter when C8RUN_CLI_NAME is set, and
// returns w unchanged otherwise.
func brandWriter(w io.Writer) io.Writer {
	if name := cliName(); name != "" {
		return cliNameWriter{w: w, name: name}
	}
	return w
}
