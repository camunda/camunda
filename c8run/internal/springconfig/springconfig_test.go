/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package springconfig

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestFilesInOrdersPropertiesBeforeYAML(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{"application.yml", "application.properties"} {
		require.NoError(t, os.WriteFile(filepath.Join(dir, name), nil, 0o644))
	}
	assert.Equal(t, []string{
		filepath.Join(dir, "application.properties"),
		filepath.Join(dir, "application.yml"),
		filepath.Join(dir, "application.yaml"),
	}, FilesIn(dir))
	assert.Equal(t, []string{"x.yaml"}, FilesIn("x.yaml"))
}

func TestLoadPropertiesAndYAMLIntoOneShape(t *testing.T) {
	dir := t.TempDir()
	props := filepath.Join(dir, "a.properties")
	yml := filepath.Join(dir, "b.yaml")
	require.NoError(t, os.WriteFile(props, []byte("# c\ncamunda.data.secondary-storage.type=elasticsearch\ncamunda.security.authentication.unprotected-api = false\n"), 0o644))
	require.NoError(t, os.WriteFile(yml, []byte("camunda.data.secondary-storage.type: opensearch\n"), 0o644))

	root, ok := Load(props)
	require.True(t, ok)
	v, _ := Lookup(root, "camunda", "data", "secondary-storage", "type")
	assert.Equal(t, "elasticsearch", v)
	b, _ := Lookup(root, "camunda", "security", "authentication", "unprotected-api")
	assert.Equal(t, false, b)

	root, ok = Load(yml)
	require.True(t, ok)
	v, _ = Lookup(root, "camunda", "data", "secondary-storage", "type")
	assert.Equal(t, "opensearch", v, "flat dotted YAML keys are expanded too")
}

func TestResolvePlaceholders(t *testing.T) {
	t.Setenv("STORAGE_TYPE", "")
	_ = os.Unsetenv("STORAGE_TYPE")
	assert.Equal(t, "elasticsearch", ResolvePlaceholders("${STORAGE_TYPE:elasticsearch}"))
	t.Setenv("STORAGE_TYPE", "opensearch")
	assert.Equal(t, "opensearch", ResolvePlaceholders("${STORAGE_TYPE:elasticsearch}"))
	assert.Equal(t, "${UNSET_NO_DEFAULT_X}", ResolvePlaceholders("${UNSET_NO_DEFAULT_X}"))

	dir := t.TempDir()
	props := filepath.Join(dir, "a.properties")
	require.NoError(t, os.WriteFile(props, []byte("camunda.data.secondary-storage.type=${STORAGE_TYPE:elasticsearch}\n"), 0o644))
	root, _ := Load(props)
	v, _ := Lookup(root, "camunda", "data", "secondary-storage", "type")
	assert.Equal(t, "opensearch", v)
}

func TestFilesInIncludesActiveProfilesFirst(t *testing.T) {
	t.Setenv("JAVA_OPTS", "")
	t.Setenv("JDK_JAVA_OPTIONS", "")
	t.Setenv("SPRING_PROFILES_ACTIVE", "")
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "application.yaml"), []byte("spring:\n  profiles:\n    active: base,prod\n"), 0o644))
	for _, name := range []string{"application-prod.yaml", "application-base.properties", "application-other.yaml"} {
		require.NoError(t, os.WriteFile(filepath.Join(dir, name), nil, 0o644))
	}
	assert.Equal(t, []string{
		filepath.Join(dir, "application-prod.yaml"),
		filepath.Join(dir, "application-base.properties"),
		filepath.Join(dir, "application.yaml"),
	}, FilesIn(dir), "the last active profile wins; inactive profiles are ignored")

	t.Setenv("SPRING_PROFILES_ACTIVE", "other")
	assert.Equal(t, filepath.Join(dir, "application-other.yaml"), FilesIn(dir)[0], "the environment beats the base file")
}
