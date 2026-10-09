//go:build linux

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDefaultDirectoryUsesXDGDataHomeOnLinux(t *testing.T) {
	dataHome := t.TempDir()
	t.Setenv("C8RUN_SECRETS_DIR", "")
	t.Setenv("XDG_DATA_HOME", dataHome)

	directory, err := ResolveDirectory(t.TempDir())

	require.NoError(t, err)
	assert.Equal(t, filepath.Join(dataHome, "camunda", "c8run", "secrets"), directory)
}

func TestDefaultDirectoryFallsBackToUserDataHomeOnLinux(t *testing.T) {
	home := t.TempDir()
	t.Setenv("C8RUN_SECRETS_DIR", "")
	t.Setenv("XDG_DATA_HOME", "")
	t.Setenv("HOME", home)

	directory, err := ResolveDirectory(t.TempDir())

	require.NoError(t, err)
	assert.Equal(t, filepath.Join(home, ".local", "share", "camunda", "c8run", "secrets"), directory)
}
