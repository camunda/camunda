/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
)

const secretReferencePrefix = "camunda.secrets."

// runEnvMode runs a secrets operation in C8RUN_SECRETS_MODE=env, where the environment c8run
// started from holds the secrets: list asks the running cluster, and nothing can be written.
func (c *secretsCommand) runEnvMode(baseDir string, args []string) error {
	switch args[0] {
	case "list":
		return c.listFromCluster(baseDir, args[1:])
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

type clusterLogin struct {
	port               int
	username, password string
}

// listFromCluster prints the secret names the running cluster resolves for this tenant. The
// cluster captured its environment at startup, which may differ from this shell's.
func (c *secretsCommand) listFromCluster(baseDir string, args []string) error {
	login, err := c.parseClusterLogin(baseDir, args)
	if err != nil {
		return err
	}
	url := physicaltenants.EndpointsFor(c.listTenant(), "http", login.port).REST + "secrets/list"
	req, err := http.NewRequest(http.MethodPost, url, strings.NewReader("{}"))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.SetBasicAuth(login.username, login.password)
	resp, err := (&http.Client{Timeout: 30 * time.Second}).Do(req)
	if err != nil {
		return fmt.Errorf("cannot reach c8run on port %d; `c8run secrets list` needs c8run running (pass --port if it was started with another port): %w", login.port, err)
	}
	defer func() { _ = resp.Body.Close() }()
	switch {
	case resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden:
		return fmt.Errorf("c8run rejected the login for %s (HTTP %d); pass the login c8run was started with as --username and --password", login.username, resp.StatusCode)
	case resp.StatusCode == http.StatusNotFound && c.tenantScoped != "":
		return fmt.Errorf("physical tenant %q is not running in c8run on port %d", c.tenantScoped, login.port)
	case resp.StatusCode >= 300:
		return fmt.Errorf("POST %s returned HTTP %d", url, resp.StatusCode)
	}
	var result struct {
		References []string `json:"references"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return fmt.Errorf("failed to read the secret list from %s: %w", url, err)
	}
	for _, reference := range result.References {
		_, _ = fmt.Fprintln(c.plain(), strings.TrimPrefix(reference, secretReferencePrefix))
	}
	return nil
}

func (c *secretsCommand) listTenant() string {
	if c.tenantScoped == "" {
		return physicaltenants.DefaultID
	}
	return c.tenantScoped
}

// parseClusterLogin reads --port, --username and --password, defaulting to start's defaults or,
// for a physical tenant, to the login c8run saved for it.
func (c *secretsCommand) parseClusterLogin(baseDir string, args []string) (clusterLogin, error) {
	login := clusterLogin{port: 8080, username: "demo", password: "demo"}
	var username, password *string
	for i := 0; i < len(args); i++ {
		flag, value, inline := strings.Cut(args[i], "=")
		if flag != "--port" && flag != "--username" && flag != "--password" {
			return login, fmt.Errorf("usage: c8run secrets list [--port <port>] [--username <name>] [--password <password>]; unsupported argument: %s", args[i])
		}
		if !inline {
			if i+1 >= len(args) {
				return login, fmt.Errorf("%s requires a value", flag)
			}
			i++
			value = args[i]
		}
		switch flag {
		case "--port":
			port, err := strconv.Atoi(value)
			if err != nil || port < 1 || port > 65535 {
				return login, fmt.Errorf("--port must be a number from 1 to 65535, got %q", value)
			}
			login.port = port
		case "--username":
			username = &value
		case "--password":
			password = &value
		}
	}
	if c.tenantScoped != "" && (username == nil || password == nil) {
		if err := c.applySavedTenantLogin(baseDir, &login); err != nil {
			return login, err
		}
	}
	if username != nil {
		login.username = *username
	}
	if password != nil {
		login.password = *password
	}
	return login, nil
}

// applySavedTenantLogin uses the login saved by `c8run physical-tenants add --username`. A tenant
// without one reuses start's login, so the defaults stay.
func (c *secretsCommand) applySavedTenantLogin(baseDir string, login *clusterLogin) error {
	path, err := physicaltenants.ResolvePath(baseDir)
	if err != nil {
		return err
	}
	tenants, passwords, err := physicaltenants.NewStore(path).Snapshot()
	if err != nil {
		return fmt.Errorf("cannot read the saved physical tenant login; pass --username and --password: %w", err)
	}
	for _, tenant := range tenants {
		if tenant.ID == c.tenantScoped && tenant.Username != "" {
			login.username, login.password = tenant.Username, passwords[tenant.ID]
		}
	}
	return nil
}
