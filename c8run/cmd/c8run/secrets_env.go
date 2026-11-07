/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"errors"
	"fmt"
	"os"
	"regexp"
	"sort"
	"strings"

	"github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
)

// runEnvMode runs a secrets operation in C8RUN_SECRETS_MODE=env, where the environment c8run
// started from holds the secrets: list reads this shell's environment, and nothing can be written.
func (c *secretsCommand) runEnvMode(baseDir string, args []string) error {
	switch args[0] {
	case "list":
		return c.listFromEnvironment(baseDir, args[1:])
	case "set", "delete":
		return envModeUnavailableError(args[0], c.listTenant(), firstName(args[1:]))
	case "import", "path":
		return envModeUnavailableError(args[0], c.listTenant(), "")
	default:
		return fmt.Errorf("unsupported secrets operation: %s", args[0])
	}
}

func envModeUnavailableError(operation, tenant, name string) error {
	message := fmt.Sprintf("%s=env reads secrets from the environment c8run starts from, so `c8run secrets %s` is not available.\n",
		localsecrets.ModeEnv, operation)
	prefix, err := localsecrets.EnvPrefix()
	if err != nil {
		return errors.New(message + fmt.Sprintf("Set %s to the prefix c8run starts with to see which variable holds a secret.", localsecrets.EnvPrefixEnv))
	}
	if name == "" {
		name = "<NAME>"
	}
	return errors.New(message + fmt.Sprintf("Set %s%s in that environment instead, then restart c8run.", physicaltenants.SecretVariablePrefix(prefix, tenant), name))
}

func firstName(args []string) string {
	for _, arg := range args {
		if !strings.HasPrefix(arg, "-") {
			return arg
		}
	}
	return ""
}

// listFromEnvironment prints the secret names this shell's environment holds for the tenant, as the
// secrets API lists them: EnvVarSecretStore matches the prefix case included and treats an empty
// value as no secret, and the API drops names outside the reference charset. c8run snapshots its
// environment at start, so this matches what it resolves when it was started from this environment.
func (c *secretsCommand) listFromEnvironment(baseDir string, args []string) error {
	if len(args) != 0 {
		return errors.New("usage: c8run secrets list")
	}
	prefix, err := localsecrets.EnvPrefix()
	if err != nil {
		return err
	}
	if c.tenantScoped != "" && !knownTenant(baseDir, c.tenantScoped) {
		_, _ = fmt.Fprintf(c.errorOutput, "Warning: physical tenant %q is not saved and was not in the last c8run start, so c8run resolves none of its secrets. Add it with `c8run physical-tenants add %s` or start with --physical-tenants.\n",
			c.tenantScoped, c.tenantScoped)
	}
	for _, name := range environmentSecretNames(os.Environ(), physicaltenants.SecretVariablePrefix(prefix, c.listTenant())) {
		_, _ = fmt.Fprintln(c.output, name)
	}
	return nil
}

var environmentSecretName = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)

func environmentSecretNames(env []string, tenantPrefix string) []string {
	var names []string
	for _, kv := range env {
		key, value, _ := strings.Cut(kv, "=")
		name, found := strings.CutPrefix(key, tenantPrefix)
		if found && value != "" && environmentSecretName.MatchString(name) {
			names = append(names, name)
		}
	}
	sort.Strings(names)
	return names
}

// knownTenant reports whether c8run has the physical tenant saved or started it last time. An
// unreadable saved file counts as known, so the warning never rests on a guess.
func knownTenant(baseDir, id string) bool {
	if _, active := physicaltenants.LastStartState(baseDir).Tenants[id]; active {
		return true
	}
	path, err := physicaltenants.ResolvePath(baseDir)
	if err != nil {
		return true
	}
	tenants, _, err := physicaltenants.NewStore(path).Snapshot()
	if err != nil {
		return true
	}
	for _, tenant := range tenants {
		if tenant.ID == id {
			return true
		}
	}
	return false
}

func (c *secretsCommand) listTenant() string {
	if c.tenantScoped == "" {
		return physicaltenants.DefaultID
	}
	return c.tenantScoped
}
