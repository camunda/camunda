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
func (c *secretsCommand) runEnvMode(args []string) error {
	switch args[0] {
	case "list":
		return c.listFromEnvironment(args[1:])
	case "set", "delete":
		return envModeWriteError(args[0], c.tenantScoped, firstName(args[1:]))
	case "import", "path":
		return envModeWriteError(args[0], c.tenantScoped, "")
	default:
		return fmt.Errorf("unsupported secrets operation: %s", args[0])
	}
}

func envModeWriteError(operation, tenant, name string) error {
	message := fmt.Sprintf("%s=env reads secrets from the environment c8run starts from, so `c8run secrets %s` is not available.\n",
		localsecrets.ModeEnv, operation)
	prefix, err := localsecrets.EnvPrefix()
	if err != nil {
		return errors.New(message + fmt.Sprintf("Set %s to the prefix c8run starts with to see which variable holds a secret.", localsecrets.EnvPrefixEnv))
	}
	if tenant == "" {
		tenant = physicaltenants.DefaultID
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

// listFromEnvironment prints the secret names this shell's environment holds for the tenant,
// under the rules EnvVarSecretStore applies: the prefix matches case included, empty values are
// no secret, and names outside the reference charset are skipped. c8run snapshots its environment
// at start, so this matches what it resolves when it was started from this environment.
func (c *secretsCommand) listFromEnvironment(args []string) error {
	if len(args) != 0 {
		return errors.New("usage: c8run secrets list")
	}
	prefix, err := localsecrets.EnvPrefix()
	if err != nil {
		return err
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

func (c *secretsCommand) listTenant() string {
	if c.tenantScoped == "" {
		return physicaltenants.DefaultID
	}
	return c.tenantScoped
}
