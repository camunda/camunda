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

func TestValuePrecedence(t *testing.T) {
	cfg := filepath.Join(t.TempDir(), "application.yaml")
	require.NoError(t, os.WriteFile(cfg, []byte("camunda.data.secondary-storage.type: rdbms\n"), 0o644))
	t.Setenv("JAVA_OPTS", "")
	t.Setenv("JDK_JAVA_OPTIONS", "")
	t.Setenv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "")
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", "")
	got, _ := Value([]string{cfg}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "rdbms", got)
	t.Setenv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", "opensearch")
	got, _ = Value([]string{cfg}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "opensearch", got)
	t.Setenv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "elasticsearch")
	got, _ = Value([]string{cfg}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "elasticsearch", got)
	t.Setenv("JDK_JAVA_OPTIONS", "-Dcamunda.data.secondary-storage.type=elasticsearch -Dcamunda.data.secondaryStorage.type=opensearch")
	got, _ = Value([]string{cfg}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "opensearch", got, "JDK_JAVA_OPTIONS beats env vars; the last -D wins")
	t.Setenv("JAVA_OPTS", "-Xmx1g -Dcamunda.data.secondary-storage.type=rdbms")
	got, _ = Value([]string{cfg}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "rdbms", got, "JAVA_OPTS is on the command line and beats JDK_JAVA_OPTIONS")
}

func TestValueReadsEveryConfigShape(t *testing.T) {
	for _, key := range []string{"JAVA_OPTS", "JDK_JAVA_OPTIONS", "CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "CAMUNDA_DATA_SECONDARY_STORAGE_TYPE"} {
		t.Setenv(key, "")
	}
	dir := t.TempDir()
	write := func(name, content string) string {
		path := filepath.Join(dir, name)
		require.NoError(t, os.WriteFile(path, []byte(content), 0o644))
		return path
	}
	cases := map[string]struct{ file, content, want string }{
		"flat dotted YAML key":    {"flat.yaml", "camunda.data.secondary-storage.type: elasticsearch\n", "elasticsearch"},
		"placeholder default":     {"placeholder.yaml", "camunda:\n  data:\n    secondary-storage:\n      type: ${C8RUN_TEST_STORAGE:elasticsearch}\n", "elasticsearch"},
		"camelCase YAML":          {"camel.yaml", "camunda:\n  data:\n    secondaryStorage:\n      type: opensearch\n", "opensearch"},
		"camelCase properties":    {"camel.properties", "camunda.data.secondaryStorage.type=elasticsearch\n", "elasticsearch"},
		"kebab-case properties":   {"kebab.properties", "camunda.data.secondary-storage.type=rdbms\n", "rdbms"},
		"no storage type present": {"empty.yaml", "camunda:\n  data: {}\n", ""},
	}
	for name, tc := range cases {
		got, _ := Value([]string{write(tc.file, tc.content)}, "camunda.data.secondary-storage.type")
		assert.Equal(t, tc.want, got, name)
	}

	t.Setenv("C8RUN_TEST_STORAGE", "opensearch")
	got, _ := Value([]string{filepath.Join(dir, "placeholder.yaml")}, "camunda.data.secondary-storage.type")
	assert.Equal(t, "opensearch", got, "the environment overrides the placeholder default")

	got, ok := Value([]string{filepath.Join(dir, "empty.yaml"), filepath.Join(dir, "kebab.properties")}, "camunda.data.secondary-storage.type")
	assert.True(t, ok)
	assert.Equal(t, "rdbms", got, "a file that does not set the key falls through to the next one")
}
