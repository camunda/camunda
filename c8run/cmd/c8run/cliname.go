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

	"github.com/camunda/camunda/c8run/internal/cliname"
)

const cliNameEnv = cliname.Env

func withCLIName(s, name string) string { return cliname.RewriteAs(s, name) }

func cliName() string { return cliname.Name() }

func brandWriter(w io.Writer) io.Writer { return cliname.Writer(w) }
