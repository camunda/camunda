/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestMain(m *testing.M) {
	if err := os.Setenv("C8RUN_SECRETS_DIR", "secrets"); err != nil {
		panic(err)
	}
	os.Exit(m.Run())
}

func TestValidateName(t *testing.T) {
	tests := []struct {
		name    string
		valid   bool
		message string
	}{
		{name: "OPENAI_API_KEY", valid: true},
		{name: "db-password", valid: true},
		{name: "1", valid: true},
		{name: "", message: "must not be empty"},
		{name: "secret.name", message: "may contain only"},
		{name: "../secret", message: "may contain only"},
		{name: "tokén", message: "may contain only"},
		{name: "CON", message: "reserved by Windows"},
		{name: "com1", message: "reserved by Windows"},
		{name: strings.Repeat("a", 241), message: "must not exceed 240"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := ValidateName(test.name)
			if test.valid {
				assert.NoError(t, err)
			} else {
				assert.ErrorContains(t, err, test.message)
			}
		})
	}
}

func TestSetCreatesAndOverwritesSecretAtomically(t *testing.T) {
	store := New(t.TempDir())

	require.NoError(t, store.Set("API_KEY", []byte("first-value")))
	require.NoError(t, store.Set("API_KEY", []byte("second-value")))

	content, err := os.ReadFile(filepath.Join(store.directory, "API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, "second-value", string(content))
	entries, err := os.ReadDir(store.directory)
	require.NoError(t, err)
	assert.Equal(t, []string{".c8run-secrets.lock", "API_KEY"}, []string{entries[0].Name(), entries[1].Name()})
	if runtime.GOOS != "windows" {
		info, err := os.Stat(filepath.Join(store.directory, "API_KEY"))
		require.NoError(t, err)
		assert.Equal(t, os.FileMode(0o600), info.Mode().Perm())
	}
}

func TestSetManyValidatesAllValuesBeforeWriting(t *testing.T) {
	store := New(t.TempDir())

	err := store.SetMany(map[string][]byte{
		"VALID":        []byte("value"),
		"INVALID.NAME": []byte("other"),
	})

	require.Error(t, err)
	_, statErr := os.Stat(store.directory)
	assert.ErrorIs(t, statErr, os.ErrNotExist)
}

func TestEnsureSecuresDirectoryOnUnix(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix permission bits are not enforced on Windows")
	}
	baseDir := t.TempDir()
	directory := filepath.Join(baseDir, DirectoryName)
	require.NoError(t, os.Mkdir(directory, 0o777))

	require.NoError(t, New(baseDir).Ensure())

	info, err := os.Stat(directory)
	require.NoError(t, err)
	assert.Equal(t, os.FileMode(0o700), info.Mode().Perm())
}

func TestEnsureSecuresExistingSecretFilesOnUnix(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix permission bits are not enforced on Windows")
	}
	baseDir := t.TempDir()
	directory := filepath.Join(baseDir, DirectoryName)
	require.NoError(t, os.Mkdir(directory, 0o700))
	secretPath := filepath.Join(directory, "API_KEY")
	require.NoError(t, os.WriteFile(secretPath, []byte("value"), 0o644))

	require.NoError(t, New(baseDir).Ensure())

	info, err := os.Stat(secretPath)
	require.NoError(t, err)
	assert.Equal(t, os.FileMode(0o600), info.Mode().Perm())
}

func TestEnsureRejectsSymbolicLink(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Creating symlinks requires additional privileges on Windows")
	}
	baseDir := t.TempDir()
	target := t.TempDir()
	require.NoError(t, os.Symlink(target, filepath.Join(baseDir, DirectoryName)))

	err := New(baseDir).Ensure()

	assert.ErrorContains(t, err, "not a symbolic link")
}

func TestEnsureRejectsSymbolicLinkAncestor(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Creating symlinks requires additional privileges on Windows")
	}
	baseDir := t.TempDir()
	target := t.TempDir()
	linkedParent := filepath.Join(baseDir, "linked-parent")
	require.NoError(t, os.Symlink(target, linkedParent))
	t.Setenv(DirectoryEnv, filepath.Join(linkedParent, "nested", "secrets"))

	err := New(baseDir).Ensure()

	assert.ErrorContains(t, err, "symbolic link")
}

func TestEnsureRejectsSecretSymlink(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Creating symlinks requires additional privileges on Windows")
	}
	baseDir := t.TempDir()
	directory := filepath.Join(baseDir, DirectoryName)
	require.NoError(t, os.Mkdir(directory, 0o700))
	target := filepath.Join(t.TempDir(), "target")
	require.NoError(t, os.WriteFile(target, []byte("value"), 0o600))
	require.NoError(t, os.Symlink(target, filepath.Join(directory, "API_KEY")))

	err := New(baseDir).Ensure()

	assert.ErrorContains(t, err, "regular file")
}

func TestEnsureRejectsUnsafeParentDirectoryOnUnix(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix permission bits are not enforced on Windows")
	}
	baseDir := filepath.Join(t.TempDir(), "shared")
	require.NoError(t, os.Mkdir(baseDir, 0o777))
	require.NoError(t, os.Chmod(baseDir, 0o777))

	err := New(baseDir).Ensure()

	assert.ErrorContains(t, err, "must not be writable by group or other users")
}

func TestListReturnsSortedNamesWithoutReadingValues(t *testing.T) {
	store := New(t.TempDir())
	require.NoError(t, store.Ensure())
	require.NoError(t, os.WriteFile(filepath.Join(store.directory, "B_SECRET"), []byte("second-value"), 0o000))
	require.NoError(t, os.WriteFile(filepath.Join(store.directory, "A_SECRET"), []byte("first-value"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(store.directory, ".temporary"), []byte("hidden-value"), 0o600))

	names, err := store.List()

	require.NoError(t, err)
	assert.Equal(t, []string{"A_SECRET", "B_SECRET"}, names)
}

func TestDeleteRemovesOnlyNamedSecret(t *testing.T) {
	store := New(t.TempDir())
	require.NoError(t, store.Set("DELETE_ME", []byte("delete-value")))
	require.NoError(t, store.Set("KEEP_ME", []byte("keep-value")))

	require.NoError(t, store.Delete("DELETE_ME"))

	_, err := os.Stat(filepath.Join(store.directory, "DELETE_ME"))
	assert.ErrorIs(t, err, os.ErrNotExist)
	_, err = os.Stat(filepath.Join(store.directory, "KEEP_ME"))
	assert.NoError(t, err)
}

func TestDeleteAllRemovesSecretsButPreservesOtherEntries(t *testing.T) {
	store := New(t.TempDir())
	require.NoError(t, store.Set("FIRST", []byte("first-value")))
	require.NoError(t, store.Set("SECOND", []byte("second-value")))
	require.NoError(t, os.WriteFile(filepath.Join(store.directory, ".keep"), []byte("keep"), 0o600))

	names, err := store.DeleteAll()

	require.NoError(t, err)
	assert.Equal(t, []string{"FIRST", "SECOND"}, names)
	remaining, err := os.ReadDir(store.directory)
	require.NoError(t, err)
	require.Len(t, remaining, 2)
	assert.Equal(t, []string{".c8run-secrets.lock", ".keep"}, []string{remaining[0].Name(), remaining[1].Name()})
}

func TestResolveDirectoryUsesEnvironmentOverride(t *testing.T) {
	baseDir := t.TempDir()
	t.Setenv("C8RUN_SECRETS_DIR", "custom-secrets")

	directory, err := ResolveDirectory(baseDir)
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(baseDir, "custom-secrets"), directory)

	absolute := filepath.Join(t.TempDir(), "external-secrets")
	t.Setenv("C8RUN_SECRETS_DIR", absolute)
	directory, err = ResolveDirectory(baseDir)
	require.NoError(t, err)
	assert.Equal(t, absolute, directory)
}

func TestResolveDirectoryRejectsExternalMode(t *testing.T) {
	t.Setenv(ModeEnv, "external")

	_, err := ResolveDirectory(t.TempDir())

	assert.ErrorContains(t, err, "C8RUN_SECRETS_MODE=external")
}

func TestModeRejectsInvalidValue(t *testing.T) {
	t.Setenv(ModeEnv, "automatic")

	_, err := Mode()

	assert.ErrorContains(t, err, "must be local or external")
}

func TestSetCreatesNestedConfiguredDirectory(t *testing.T) {
	baseDir := t.TempDir()
	t.Setenv("C8RUN_SECRETS_DIR", filepath.Join("temporary", "nested", "secrets"))
	store := New(baseDir)

	require.NoError(t, store.Set("API_KEY", []byte("value")))

	content, err := os.ReadFile(filepath.Join(baseDir, "temporary", "nested", "secrets", "API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, "value", string(content))
}

func TestImportWritesDotenvValuesAndReportsNames(t *testing.T) {
	store := New(t.TempDir())
	input := "# local secrets\nB_SECRET='two words'\nA_SECRET=one\nEMPTY=\n"

	names, err := store.Import(strings.NewReader(input))

	require.NoError(t, err)
	assert.Equal(t, []string{"A_SECRET", "B_SECRET", "EMPTY"}, names)
	for name, expected := range map[string]string{"A_SECRET": "one", "B_SECRET": "two words", "EMPTY": ""} {
		content, err := os.ReadFile(filepath.Join(store.directory, name))
		require.NoError(t, err)
		assert.Equal(t, expected, string(content))
	}
}

func TestImportAcceptsDashedNamesAndPreservesDollarSigns(t *testing.T) {
	store := New(t.TempDir())

	names, err := store.Import(strings.NewReader("base=value\nopenai-api-key=$base-literal\n"))

	require.NoError(t, err)
	assert.Equal(t, []string{"base", "openai-api-key"}, names)
	content, err := os.ReadFile(filepath.Join(store.directory, "openai-api-key"))
	require.NoError(t, err)
	assert.Equal(t, "$base-literal", string(content))
}

func TestImportPreservesAssignmentTextInsideMultilineValue(t *testing.T) {
	store := New(t.TempDir())

	names, err := store.Import(strings.NewReader("MULTI=\"first\ninner=value\nlast\"\n"))

	require.NoError(t, err)
	assert.Equal(t, []string{"MULTI"}, names)
	content, err := os.ReadFile(filepath.Join(store.directory, "MULTI"))
	require.NoError(t, err)
	assert.Equal(t, "first\ninner=value\nlast", string(content))
}

func TestImportAcceptsApostropheInUnquotedValue(t *testing.T) {
	store := New(t.TempDir())

	_, err := store.Import(strings.NewReader("MESSAGE=it's-safe\nOTHER=value\n"))

	require.NoError(t, err)
	content, err := os.ReadFile(filepath.Join(store.directory, "MESSAGE"))
	require.NoError(t, err)
	assert.Equal(t, "it's-safe", string(content))
}

func TestImportRejectsDuplicateAndCaseCollidingNames(t *testing.T) {
	store := New(t.TempDir())

	_, err := store.Import(strings.NewReader("API_KEY=one\nAPI_KEY=two\n"))
	assert.ErrorContains(t, err, "duplicate")

	_, err = store.Import(strings.NewReader("API_KEY=one\napi_key=two\n"))
	assert.ErrorContains(t, err, "differ only by case")
}

func TestSetRejectsExistingCaseCollision(t *testing.T) {
	store := New(t.TempDir())
	require.NoError(t, store.Set("API_KEY", []byte("one")))

	err := store.Set("api_key", []byte("two"))

	assert.ErrorContains(t, err, "case-insensitive")
}

func TestSetRejectsOversizedValue(t *testing.T) {
	err := New(t.TempDir()).Set("API_KEY", make([]byte, MaxSecretSize+1))

	assert.ErrorContains(t, err, "must not exceed")
}

func TestImportValidatesAllNamesBeforeWriting(t *testing.T) {
	store := New(t.TempDir())
	sentinel := "value-that-must-not-be-disclosed"

	_, err := store.Import(strings.NewReader("VALID=" + sentinel + "\nINVALID.NAME=other\n"))

	require.Error(t, err)
	assert.NotContains(t, err.Error(), sentinel)
	_, statErr := os.Stat(filepath.Join(store.directory, "VALID"))
	assert.ErrorIs(t, statErr, os.ErrNotExist)
}

func TestTrimInputLineEnding(t *testing.T) {
	assert.Equal(t, "value", string(TrimInputLineEnding([]byte("value\n"))))
	assert.Equal(t, "value", string(TrimInputLineEnding([]byte("value\r\n"))))
	assert.Equal(t, "value\n", string(TrimInputLineEnding([]byte("value\n\n"))))
}
