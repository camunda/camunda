/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
)

type secretsCommand struct {
	input        io.Reader
	output       io.Writer
	errorOutput  io.Writer
	isTerminal   func() bool
	readPassword func() ([]byte, error)
}

func (c *secretsCommand) warnPathChanged(baseDir string) {
	current, err := localsecrets.ResolveDirectory(baseDir)
	if err != nil {
		return
	}
	defaultDirectory, err := localsecrets.DefaultDirectory()
	if err != nil || defaultDirectory == current {
		return
	}
	entries, err := os.ReadDir(defaultDirectory)
	if err != nil || len(entries) == 0 {
		return
	}
	_, _ = fmt.Fprintf(c.errorOutput, "Warning: the platform-default directory also contains entries:\n  %s\nThis command uses:\n  %s\nReview both directories and remove unused secrets deliberately.\n", defaultDirectory, current)
}

func newSecretsCommand() *secretsCommand {
	return &secretsCommand{
		input:        os.Stdin,
		output:       os.Stdout,
		errorOutput:  os.Stderr,
		isTerminal:   stdinIsTerminal,
		readPassword: readSecretFromTerminal,
	}
}

func (c *secretsCommand) run(baseDir string, args []string) error {
	if len(args) == 0 {
		return errors.New("usage: c8run secrets <set|list|path|delete|import>")
	}
	if args[0] == "help" || args[0] == "-h" || args[0] == "--help" {
		_, _ = fmt.Fprint(c.output, secretsHelp)
		return nil
	}
	if len(args) == 2 && (args[1] == "help" || args[1] == "-h" || args[1] == "--help") {
		if help, exists := secretsCommandHelp[args[0]]; exists {
			_, _ = fmt.Fprint(c.output, help)
			return nil
		}
	}
	store := localsecrets.New(baseDir)
	c.warnPathChanged(baseDir)

	switch args[0] {
	case "set":
		return c.set(store, args[1:])
	case "list":
		if len(args) != 1 {
			return errors.New("usage: c8run secrets list")
		}
		return c.list(store)
	case "path":
		if len(args) != 1 {
			return errors.New("usage: c8run secrets path")
		}
		directory, err := localsecrets.ResolveDirectory(baseDir)
		if err != nil {
			return err
		}
		_, _ = fmt.Fprintln(c.output, directory)
		return nil
	case "delete":
		return c.delete(store, args[1:])
	case "import":
		return c.importDotenv(store, args[1:])
	default:
		return fmt.Errorf("unsupported secrets operation: %s", args[0])
	}
}

func (c *secretsCommand) set(store *localsecrets.Store, args []string) error {
	names, fromStdin, err := parseSetArguments(args)
	if err != nil {
		return err
	}
	seenNames := make(map[string]string, len(names))
	for _, name := range names {
		if err := localsecrets.ValidateName(name); err != nil {
			return fmt.Errorf("invalid secret name %q: %w", name, err)
		}
		folded := strings.ToLower(name)
		if existing, exists := seenNames[folded]; exists {
			return fmt.Errorf("secret names %q and %q are duplicates or differ only by case", existing, name)
		}
		seenNames[folded] = name
	}

	if fromStdin {
		existed, err := store.Exists(names[0])
		if err != nil {
			return err
		}
		value, err := io.ReadAll(io.LimitReader(c.input, localsecrets.MaxSecretSize+1))
		if len(value) > localsecrets.MaxSecretSize {
			return fmt.Errorf("secret value must not exceed %d bytes", localsecrets.MaxSecretSize)
		}
		value = localsecrets.TrimInputLineEnding(value)
		if err != nil {
			return errors.New("failed to read secret value")
		}
		if err := withoutInterrupts(func() error { return store.Set(names[0], value) }); err != nil {
			return err
		}
		c.printSecretSaved(names[0])
		if existed {
			c.printCacheWarning("previous value")
		}
		return nil
	}

	if !c.isTerminal() {
		if os.Getenv("MSYSTEM") != "" {
			return errors.New("git Bash does not expose its terminal directly; run with winpty or use --stdin")
		}
		return errors.New("interactive input requires a terminal; use --stdin for automation")
	}
	values := make(map[string][]byte, len(names))
	overwrote := false
	for _, name := range names {
		existed, err := store.Exists(name)
		if err != nil {
			return err
		}
		overwrote = overwrote || existed
		_, _ = fmt.Fprintf(c.errorOutput, "Enter value for %s: ", name)
		value, err := c.readPassword()
		_, _ = fmt.Fprintln(c.errorOutput)
		if err != nil {
			return fmt.Errorf("failed to read value for secret %q", name)
		}
		values[name] = value
	}
	if err := withoutInterrupts(func() error { return store.SetMany(values) }); err != nil {
		return err
	}
	for _, name := range names {
		c.printSecretSaved(name)
	}
	if overwrote {
		c.printCacheWarning("previous values")
	}
	return nil
}

func parseSetArguments(args []string) ([]string, bool, error) {
	var names []string
	fromStdin := false
	for _, argument := range args {
		if argument == "--stdin" {
			if fromStdin {
				return nil, false, errors.New("--stdin may only be specified once")
			}
			fromStdin = true
			continue
		}
		if strings.HasPrefix(argument, "-") {
			return nil, false, fmt.Errorf("unsupported option: %s", argument)
		}
		names = append(names, argument)
	}
	if len(names) == 0 {
		return nil, false, errors.New("usage: c8run secrets set <name> [name...] [--stdin]")
	}
	if fromStdin && len(names) != 1 {
		return nil, false, errors.New("--stdin accepts exactly one secret name; use dotenv import for multiple values")
	}
	return names, fromStdin, nil
}

func (c *secretsCommand) printSecretSaved(name string) {
	_, _ = fmt.Fprintf(c.output, "Secret %s saved.\n\nReference it in Process Models with:\n%s\n", name, feelReference(name))
}

func (c *secretsCommand) printCacheWarning(subject string) {
	_, _ = fmt.Fprintf(c.errorOutput, "If c8run has cached this secret, it may continue using %s until the cache expires. Restart c8run to apply the change immediately.\n", subject)
}

func (c *secretsCommand) list(store *localsecrets.Store) error {
	names, err := store.List()
	if err != nil {
		return err
	}
	for _, name := range names {
		_, _ = fmt.Fprintln(c.output, name)
	}
	return nil
}

func (c *secretsCommand) delete(store *localsecrets.Store, args []string) error {
	name, deleteAll, confirmed, err := parseDeleteArguments(args)
	if err != nil {
		return err
	}
	if !deleteAll {
		if err := localsecrets.ValidateName(name); err != nil {
			return err
		}
	}
	if !c.isTerminal() && !confirmed {
		return errors.New("non-interactive deletion requires --yes")
	}
	description := "secret " + name
	if deleteAll {
		description = "all local secrets"
	}
	if c.isTerminal() && !confirmed {
		_, _ = fmt.Fprintf(c.errorOutput, "Delete %s? [y/N]: ", description)
		answer, err := bufio.NewReader(c.input).ReadString('\n')
		if err != nil && !errors.Is(err, io.EOF) {
			return errors.New("failed to read confirmation")
		}
		answer = strings.TrimSpace(answer)
		if !strings.EqualFold(answer, "y") && !strings.EqualFold(answer, "yes") {
			_, _ = fmt.Fprintln(c.output, "No secrets deleted.")
			return nil
		}
	}
	if deleteAll {
		names, err := store.DeleteAll()
		if err != nil {
			return err
		}
		_, _ = fmt.Fprintf(c.output, "Deleted %d secret(s).\n", len(names))
		if len(names) > 0 {
			c.printCacheWarning("deleted values")
		}
		return nil
	}
	if err := store.Delete(name); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(c.output, "Secret %s deleted.\n", name)
	c.printCacheWarning("the deleted value")
	return nil
}

func parseDeleteArguments(args []string) (string, bool, bool, error) {
	var name string
	deleteAll := false
	confirmed := false
	for _, argument := range args {
		if argument == "--yes" {
			if confirmed {
				return "", false, false, errors.New("--yes may only be specified once")
			}
			confirmed = true
			continue
		}
		if argument == "--all" {
			if deleteAll {
				return "", false, false, errors.New("--all may only be specified once")
			}
			deleteAll = true
			continue
		}
		if strings.HasPrefix(argument, "-") {
			return "", false, false, fmt.Errorf("unsupported option: %s", argument)
		}
		if name != "" {
			return "", false, false, errors.New("usage: c8run secrets delete (<name>|--all) [--yes]")
		}
		name = argument
	}
	if (name == "") == !deleteAll {
		return "", false, false, errors.New("usage: c8run secrets delete (<name>|--all) [--yes]")
	}
	return name, deleteAll, confirmed, nil
}

func (c *secretsCommand) importDotenv(store *localsecrets.Store, args []string) error {
	if len(args) > 1 {
		return errors.New("usage: c8run secrets import [dotenv-file]")
	}
	reader := c.input
	if len(args) == 1 && args[0] != "-" {
		importPath, err := filepath.Abs(args[0])
		if err != nil {
			return fmt.Errorf("failed to resolve dotenv file %q", args[0])
		}
		metadataPath, err := filepath.Abs(".env")
		if err != nil {
			return errors.New("failed to resolve the c8run .env path")
		}
		file, err := os.Open(importPath)
		if err != nil {
			return fmt.Errorf("failed to open dotenv file %q: %w", args[0], err)
		}
		importInfo, importErr := file.Stat()
		metadataFile, metadataOpenErr := os.Open(metadataPath)
		var metadataInfo os.FileInfo
		var metadataErr error
		if metadataOpenErr == nil {
			defer func() { _ = metadataFile.Close() }()
			metadataInfo, metadataErr = metadataFile.Stat()
		}
		if importErr != nil {
			_ = file.Close()
			return fmt.Errorf("failed to inspect dotenv file %q", args[0])
		}
		if metadataOpenErr == nil && metadataErr == nil && os.SameFile(importInfo, metadataInfo) {
			_ = file.Close()
			return errors.New("c8run secrets refuses to import the c8run .env file because it contains runtime and packaging configuration; use a dedicated secrets dotenv file")
		}
		defer func() { _ = file.Close() }()
		reader = file
	}

	before, err := store.List()
	if err != nil {
		return err
	}
	existing := make(map[string]struct{}, len(before))
	for _, name := range before {
		existing[name] = struct{}{}
	}
	values, names, err := localsecrets.ParseImport(reader)
	if err != nil {
		return err
	}
	err = withoutInterrupts(func() error { return store.SetMany(values) })
	if err != nil {
		return err
	}
	for _, name := range names {
		_, _ = fmt.Fprintln(c.output, name)
	}
	_, _ = fmt.Fprintf(c.output, "Imported %d secret(s).\n", len(names))
	for _, name := range names {
		if _, overwritten := existing[name]; overwritten {
			c.printCacheWarning("previous values")
			break
		}
	}
	return nil
}

func feelReference(name string) string {
	if strings.Contains(name, "-") {
		return "=camunda.secrets.`" + name + "`"
	}
	return "=camunda.secrets." + name
}

const secretsHelp = `Usage:
  c8run secrets set <name> [name...] [--stdin]
  c8run secrets list
  c8run secrets path
  c8run secrets delete (<name>|--all) [--yes]
  c8run secrets import [dotenv-file]

Secret names may contain letters, numbers, underscores, and dashes.
Use backticks around names containing dashes in FEEL expressions.
Import reads dotenv input from stdin when no file is specified.
The default directory is shared across c8run versions and projects for the current OS user.
C8RUN_SECRETS_DIR selects another directory; relative paths use the current working directory.
C8RUN_SECRETS_MODE defaults to local; external disables local secret commands.
Overwrites and deletions may remain cached until c8run restarts or the cache expires.
Local secret commands do not manage stores configured explicitly through --config, such as AWS or GCP.
`

var secretsCommandHelp = map[string]string{
	"set":    "Usage: c8run secrets set <name> [name...] [--stdin]\nPrompts without echo for each value. --stdin accepts exactly one secret.\n",
	"list":   "Usage: c8run secrets list\nLists names only. Secret values are never displayed.\n",
	"path":   "Usage: c8run secrets path\nShows the c8run-managed local directory without creating it.\n",
	"delete": "Usage: c8run secrets delete (<name>|--all) [--yes]\nInteractive use asks for confirmation. Non-interactive use requires --yes.\n",
	"import": "Usage: c8run secrets import [dotenv-file]\nImports dotenv entries from a file, or from stdin when no file is provided. Import is additive.\n",
}
