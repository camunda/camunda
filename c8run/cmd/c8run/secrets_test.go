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
	"errors"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestMain(m *testing.M) {
	if err := os.Setenv("C8RUN_SECRETS_DIR", "secrets"); err != nil {
		panic(err)
	}
	os.Exit(m.Run())
}

func testSecretsCommand(input string, terminal bool) (*secretsCommand, *bytes.Buffer, *bytes.Buffer) {
	output := &bytes.Buffer{}
	errorOutput := &bytes.Buffer{}
	return &secretsCommand{
		input:       strings.NewReader(input),
		output:      output,
		errorOutput: errorOutput,
		isTerminal: func() bool {
			return terminal
		},
		readPassword: func() ([]byte, error) {
			return []byte(input), nil
		},
	}, output, errorOutput
}

func TestSecretsSetFromStdinDoesNotPrintValue(t *testing.T) {
	baseDir := t.TempDir()
	sentinel := "value-that-must-not-be-disclosed"
	command, output, errorOutput := testSecretsCommand(sentinel+"\n", false)

	err := command.run(baseDir, []string{"set", "OPENAI_API_KEY", "--stdin"})

	require.NoError(t, err)
	assert.NotContains(t, output.String(), sentinel)
	assert.Empty(t, errorOutput.String())
	assert.Contains(t, output.String(), "=camunda.secrets.OPENAI_API_KEY")
	content, err := os.ReadFile(filepath.Join(baseDir, "secrets", "OPENAI_API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, sentinel, string(content))
}

func TestSecretsSetPromptsWithoutUsingInputStream(t *testing.T) {
	baseDir := t.TempDir()
	command, output, errorOutput := testSecretsCommand("interactive-value", true)

	err := command.run(baseDir, []string{"set", "db-password"})

	require.NoError(t, err)
	assert.Equal(t, "Enter value for db-password: \n", errorOutput.String())
	assert.Contains(t, output.String(), "=camunda.secrets.`db-password`")
	assert.Contains(t, output.String(), "Reference it in Process Models with:")
	assert.NotContains(t, output.String(), "interactive-value")
}

func TestSecretsSetRequiresStdinFlagWhenNotInteractive(t *testing.T) {
	command, _, _ := testSecretsCommand("secret-value", false)

	err := command.run(t.TempDir(), []string{"set", "API_KEY"})

	assert.ErrorContains(t, err, "use --stdin")
	assert.NotContains(t, err.Error(), "secret-value")
}

func TestSecretsSetRejectsOversizedStdin(t *testing.T) {
	command, _, _ := testSecretsCommand(strings.Repeat("x", 1024*1024+1), false)

	err := command.run(t.TempDir(), []string{"set", "API_KEY", "--stdin"})

	assert.ErrorContains(t, err, "must not exceed")
}

func TestSecretsSetAllowsMaximumSizeWithTrailingNewline(t *testing.T) {
	command, _, _ := testSecretsCommand(strings.Repeat("x", localsecrets.MaxSecretSize)+"\n", false)
	baseDir := t.TempDir()

	err := command.run(baseDir, []string{"set", "API_KEY", "--stdin"})

	require.NoError(t, err)
	value, err := os.ReadFile(filepath.Join(baseDir, "secrets", "API_KEY"))
	require.NoError(t, err)
	assert.Len(t, value, localsecrets.MaxSecretSize)
}

func TestSecretsSetReportsStdinReadFailure(t *testing.T) {
	command, _, _ := testSecretsCommand("", false)
	command.input = failingReader{}

	err := command.run(t.TempDir(), []string{"set", "API_KEY", "--stdin"})

	assert.ErrorContains(t, err, "failed to read secret value: test read failure")
}

type failingReader struct{}

func (failingReader) Read([]byte) (int, error) {
	return 0, errors.New("test read failure")
}

func TestSecretsSetRejectsInvalidUTF8FromStdin(t *testing.T) {
	command, _, _ := testSecretsCommand(string([]byte{0xff}), false)

	err := command.run(t.TempDir(), []string{"set", "API_KEY", "--stdin"})

	assert.ErrorContains(t, err, "valid UTF-8")
}

func TestSecretsListPrintsNamesOnly(t *testing.T) {
	baseDir := t.TempDir()
	setup, _, _ := testSecretsCommand("", false)
	require.NoError(t, setup.run(baseDir, []string{"set", "B_SECRET", "--stdin"}))
	require.NoError(t, setup.run(baseDir, []string{"set", "A_SECRET", "--stdin"}))
	command, output, _ := testSecretsCommand("", false)

	err := command.run(baseDir, []string{"list"})

	require.NoError(t, err)
	assert.Equal(t, "A_SECRET\nB_SECRET\n", output.String())
}

func TestSecretsPathPrintsNormalizedConfiguredDirectoryWithoutCreatingIt(t *testing.T) {
	baseDir := t.TempDir()
	t.Setenv("C8RUN_SECRETS_DIR", filepath.Join("temporary", "..", "configured-secrets"))
	command, output, _ := testSecretsCommand("", false)

	err := command.run(baseDir, []string{"path"})

	require.NoError(t, err)
	expected := filepath.Join(baseDir, "configured-secrets")
	assert.Equal(t, expected+"\n", output.String())
	_, statErr := os.Stat(expected)
	assert.ErrorIs(t, statErr, os.ErrNotExist)
}

func TestSecretsPathRejectsArguments(t *testing.T) {
	command, _, _ := testSecretsCommand("", false)

	err := command.run(t.TempDir(), []string{"path", "extra"})

	assert.ErrorContains(t, err, "usage: c8run secrets path")
}

func TestSecretsDeleteRequiresInteractiveConfirmation(t *testing.T) {
	baseDir := t.TempDir()
	setup, _, _ := testSecretsCommand("value", false)
	require.NoError(t, setup.run(baseDir, []string{"set", "API_KEY", "--stdin"}))
	command, output, errorOutput := testSecretsCommand("no\n", true)

	err := command.run(baseDir, []string{"delete", "API_KEY"})

	require.NoError(t, err)
	assert.Equal(t, "No secrets deleted.\n", output.String())
	assert.Contains(t, errorOutput.String(), "Delete secret API_KEY?")
	_, err = os.Stat(filepath.Join(baseDir, "secrets", "API_KEY"))
	assert.NoError(t, err)
}

func TestSecretsDeleteWithoutTerminalSupportsAutomation(t *testing.T) {
	baseDir := t.TempDir()
	setup, _, _ := testSecretsCommand("value", false)
	require.NoError(t, setup.run(baseDir, []string{"set", "API_KEY", "--stdin"}))
	command, output, _ := testSecretsCommand("", false)

	err := command.run(baseDir, []string{"delete", "API_KEY", "--yes"})

	require.NoError(t, err)
	assert.Equal(t, "Secret API_KEY deleted.\n", output.String())
}

func TestSecretsDeleteWithoutTerminalRequiresYes(t *testing.T) {
	baseDir := t.TempDir()
	setup, _, _ := testSecretsCommand("value", false)
	require.NoError(t, setup.run(baseDir, []string{"set", "API_KEY", "--stdin"}))
	command, _, _ := testSecretsCommand("yes\n", false)

	err := command.run(baseDir, []string{"delete", "API_KEY"})

	assert.ErrorContains(t, err, "requires --yes")
	_, statErr := os.Stat(filepath.Join(baseDir, "secrets", "API_KEY"))
	assert.NoError(t, statErr)
}

func TestSecretsDeleteRejectsInvalidNameBeforePrompting(t *testing.T) {
	command, _, errorOutput := testSecretsCommand("yes\n", true)

	err := command.run(t.TempDir(), []string{"delete", "invalid.name"})

	assert.ErrorContains(t, err, "may contain only")
	assert.Empty(t, errorOutput.String())
}

func TestSecretsDeleteAllRequiresConfirmationAndReportsCount(t *testing.T) {
	baseDir := t.TempDir()
	setup, _, _ := testSecretsCommand("value", false)
	require.NoError(t, setup.run(baseDir, []string{"set", "FIRST", "--stdin"}))
	setup.input = strings.NewReader("value")
	require.NoError(t, setup.run(baseDir, []string{"set", "SECOND", "--stdin"}))
	command, output, errorOutput := testSecretsCommand("yes\n", true)

	err := command.run(baseDir, []string{"delete", "--all"})

	require.NoError(t, err)
	assert.Equal(t, "Deleted 2 secret(s).\n", output.String())
	assert.Contains(t, errorOutput.String(), "Delete all local secrets?")
	names, err := os.ReadDir(filepath.Join(baseDir, "secrets"))
	require.NoError(t, err)
	require.Len(t, names, 1)
	assert.Equal(t, ".c8run-secrets.lock", names[0].Name())
}

func TestSecretsDeleteAllNonInteractiveRequiresYes(t *testing.T) {
	command, _, _ := testSecretsCommand("", false)

	err := command.run(t.TempDir(), []string{"delete", "--all"})

	assert.ErrorContains(t, err, "requires --yes")
}

func TestSecretsCommandsUseConfiguredDirectory(t *testing.T) {
	baseDir := t.TempDir()
	configuredDirectory := filepath.Join(t.TempDir(), "configured-secrets")
	t.Setenv("C8RUN_SECRETS_DIR", configuredDirectory)
	command, _, _ := testSecretsCommand("secret-value", false)

	err := command.run(baseDir, []string{"set", "API_KEY", "--stdin"})

	require.NoError(t, err)
	content, err := os.ReadFile(filepath.Join(configuredDirectory, "API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, "secret-value", string(content))
}

func TestSecretsCommandsAreDisabledInExternalMode(t *testing.T) {
	baseDir := t.TempDir()
	t.Setenv("C8RUN_SECRETS_MODE", "external")
	command, _, _ := testSecretsCommand("secret-value", false)

	err := command.run(baseDir, []string{"set", "API_KEY", "--stdin"})

	assert.ErrorContains(t, err, "C8RUN_SECRETS_MODE=external")
	_, statErr := os.Stat(filepath.Join(baseDir, "secrets"))
	assert.ErrorIs(t, statErr, os.ErrNotExist)
}

func TestSecretsImportDoesNotPrintValues(t *testing.T) {
	baseDir := t.TempDir()
	sentinel := "value-that-must-not-be-disclosed"
	command, output, errorOutput := testSecretsCommand("API_KEY="+sentinel+"\nOTHER=other-value\n", false)

	err := command.run(baseDir, []string{"import"})

	require.NoError(t, err)
	assert.Equal(t, "API_KEY\nOTHER\nImported 2 secret(s).\n", output.String())
	assert.NotContains(t, output.String(), sentinel)
	assert.NotContains(t, errorOutput.String(), sentinel)
}

func TestSecretsImportReadsInputBeforeWriting(t *testing.T) {
	baseDir := t.TempDir()
	reader := &importOrderReader{
		content:    []byte("API_KEY=value\n"),
		secretPath: filepath.Join(baseDir, "secrets", "API_KEY"),
	}
	command, _, _ := testSecretsCommand("", false)
	command.input = reader

	err := command.run(baseDir, []string{"import"})

	require.NoError(t, err)
	assert.True(t, reader.reachedEOF)
	content, err := os.ReadFile(filepath.Join(baseDir, "secrets", "API_KEY"))
	require.NoError(t, err)
	assert.Equal(t, "value", string(content))
}

type importOrderReader struct {
	content    []byte
	secretPath string
	reachedEOF bool
}

func (r *importOrderReader) Read(destination []byte) (int, error) {
	if len(r.content) == 0 {
		r.reachedEOF = true
		return 0, io.EOF
	}
	if _, err := os.Stat(r.secretPath); !errors.Is(err, os.ErrNotExist) {
		return 0, errors.New("secret was written before import reached EOF")
	}
	count := copy(destination, r.content)
	r.content = r.content[count:]
	return count, nil
}

func TestSecretsImportRefusesC8RunMetadataEnv(t *testing.T) {
	baseDir := t.TempDir()
	metadata := filepath.Join(baseDir, ".env")
	require.NoError(t, os.WriteFile(metadata, []byte("JAVA_ARTIFACTS_PASSWORD=must-not-import\n"), 0o600))
	oldWorkingDirectory, err := os.Getwd()
	require.NoError(t, err)
	require.NoError(t, os.Chdir(baseDir))
	t.Cleanup(func() { _ = os.Chdir(oldWorkingDirectory) })
	command, output, errorOutput := testSecretsCommand("", false)

	err = command.run(baseDir, []string{"import", filepath.Join(".", ".env")})

	assert.ErrorContains(t, err, "c8run secrets refuses to import the c8run .env")
	assert.NotContains(t, output.String(), "must-not-import")
	assert.NotContains(t, errorOutput.String(), "must-not-import")
	_, statErr := os.Stat(filepath.Join(baseDir, "secrets", "JAVA_ARTIFACTS_PASSWORD"))
	assert.ErrorIs(t, statErr, os.ErrNotExist)
}

func TestSecretsImportRefusesSymlinkToC8RunMetadataEnv(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Creating symlinks requires additional privileges on Windows")
	}
	baseDir := t.TempDir()
	metadata := filepath.Join(baseDir, ".env")
	link := filepath.Join(baseDir, "secrets.env")
	require.NoError(t, os.WriteFile(metadata, []byte("JAVA_ARTIFACTS_PASSWORD=must-not-import\n"), 0o600))
	require.NoError(t, os.Symlink(metadata, link))
	oldWorkingDirectory, err := os.Getwd()
	require.NoError(t, err)
	require.NoError(t, os.Chdir(baseDir))
	t.Cleanup(func() { _ = os.Chdir(oldWorkingDirectory) })
	command, _, _ := testSecretsCommand("", false)

	err = command.run(baseDir, []string{"import", link})

	assert.ErrorContains(t, err, "c8run secrets refuses to import the c8run .env")
}

func TestSecretsImportRefusesHardLinkToC8RunMetadataEnv(t *testing.T) {
	baseDir := t.TempDir()
	metadata := filepath.Join(baseDir, ".env")
	link := filepath.Join(baseDir, "secrets.env")
	require.NoError(t, os.WriteFile(metadata, []byte("JAVA_ARTIFACTS_PASSWORD=must-not-import\n"), 0o600))
	require.NoError(t, os.Link(metadata, link))
	oldWorkingDirectory, err := os.Getwd()
	require.NoError(t, err)
	require.NoError(t, os.Chdir(baseDir))
	t.Cleanup(func() { _ = os.Chdir(oldWorkingDirectory) })
	command, _, _ := testSecretsCommand("", false)

	err = command.run(baseDir, []string{"import", link})

	assert.ErrorContains(t, err, "c8run secrets refuses to import the c8run .env")
}

func TestSecretsSetHasSubcommandHelp(t *testing.T) {
	command, output, _ := testSecretsCommand("", false)

	err := command.run(t.TempDir(), []string{"set", "--help"})

	require.NoError(t, err)
	assert.Contains(t, output.String(), "Usage: c8run secrets set")
}

func TestSecretsSetPromptsForMultipleNames(t *testing.T) {
	baseDir := t.TempDir()
	values := [][]byte{[]byte("first-value"), []byte("second-value")}
	read := 0
	command, output, errorOutput := testSecretsCommand("", true)
	command.readPassword = func() ([]byte, error) {
		value := values[read]
		read++
		return value, nil
	}

	err := command.run(baseDir, []string{"set", "FIRST", "SECOND"})

	require.NoError(t, err)
	assert.Equal(t, 2, read)
	assert.Contains(t, errorOutput.String(), "Enter value for FIRST:")
	assert.Contains(t, errorOutput.String(), "Enter value for SECOND:")
	assert.NotContains(t, output.String(), "first-value")
	assert.NotContains(t, output.String(), "second-value")
	for name, expected := range map[string]string{"FIRST": "first-value", "SECOND": "second-value"} {
		content, err := os.ReadFile(filepath.Join(baseDir, "secrets", name))
		require.NoError(t, err)
		assert.Equal(t, expected, string(content))
	}
}

func TestSecretsSetStdinRejectsMultipleNames(t *testing.T) {
	command, _, _ := testSecretsCommand("secret-value", false)

	err := command.run(t.TempDir(), []string{"set", "FIRST", "SECOND", "--stdin"})

	assert.ErrorContains(t, err, "accepts exactly one secret name")
	assert.NotContains(t, err.Error(), "secret-value")
}

func TestSecretsSetRejectsDuplicateNamesBeforePrompting(t *testing.T) {
	command, _, errorOutput := testSecretsCommand("", true)

	err := command.run(t.TempDir(), []string{"set", "API_KEY", "api_key"})

	assert.ErrorContains(t, err, "differ only by case")
	assert.Empty(t, errorOutput.String())
}

func TestSecretsSetRejectsInvalidNameBeforePrompting(t *testing.T) {
	command, _, errorOutput := testSecretsCommand("secret-value", true)

	err := command.run(t.TempDir(), []string{"set", "invalid.name"})

	assert.ErrorContains(t, err, "may contain only")
	assert.Empty(t, errorOutput.String())
}

func TestSecretsHelpDocumentsCommandsAndDashedNames(t *testing.T) {
	command, output, _ := testSecretsCommand("", false)

	err := command.run(t.TempDir(), []string{"--help"})

	require.NoError(t, err)
	assert.Contains(t, output.String(), "secrets import [dotenv-file]")
	assert.Contains(t, output.String(), "backticks")
	assert.Contains(t, output.String(), "shared across c8run versions and projects")
}
