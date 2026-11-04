/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/camunda/camunda/c8run/internal/types"
)

const defaultEnvSecretStorePrefixEnv = "CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX"

// configureSecretStores sets up each tenant's secret store for C8RUN_SECRETS_MODE before any
// process starts: a local file store per tenant, an environment-variable store per tenant, or
// nothing when the user configures stores in --config.
func configureSecretStores(baseDir string, settings *types.C8RunSettings) error {
	mode, err := localsecrets.Mode()
	if err != nil {
		return err
	}
	switch mode {
	case "local":
		return configureLocalSecretStores(baseDir, settings)
	case "env":
		return configureEnvSecretStores(settings)
	default:
		if len(settings.PhysicalTenants) > 0 {
			return fmt.Errorf("physical tenants managed by c8run need their own secret stores, which c8run only sets up when %s=local or %s=env.\n"+
				"Either set %s to local or env, or declare the physical tenants and their secret stores in your --config "+
				"(camunda.physical-tenants.<id>.secrets.stores.*)", localsecrets.ModeEnv, localsecrets.ModeEnv, localsecrets.ModeEnv)
		}
		return nil
	}
}

func configureLocalSecretStores(baseDir string, settings *types.C8RunSettings) error {
	secretStore := localsecrets.New(baseDir)
	if err := secretStore.Ensure(); err != nil {
		return err
	}
	secretDirectory, err := secretStore.Directory()
	if err != nil {
		return err
	}
	if err := os.Setenv(localsecrets.DirectoryEnv, secretDirectory); err != nil {
		return fmt.Errorf("failed to configure local secrets directory: %w", err)
	}
	if err := os.Setenv("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH", secretDirectory); err != nil {
		return fmt.Errorf("failed to configure Camunda secret store: %w", err)
	}
	return configureTenantSecretStores(baseDir, settings)
}

func configureEnvSecretStores(settings *types.C8RunSettings) error {
	prefix, err := localsecrets.EnvPrefix()
	if err != nil {
		return err
	}
	if len(settings.PhysicalTenantsDeclaredIn) > 0 {
		return fmt.Errorf("%s=env sets up secret stores only for physical tenants c8run manages, but physical tenants are declared in %s.\n"+
			"Either manage them with `c8run physical-tenants`, or set %s=external and declare each tenant's secret store in your --config "+
			"(camunda.physical-tenants.<id>.secrets.stores.*)",
			localsecrets.ModeEnv, strings.Join(settings.PhysicalTenantsDeclaredIn, " and "), localsecrets.ModeEnv)
	}
	if err := os.Setenv(defaultEnvSecretStorePrefixEnv, physicaltenants.SecretVariablePrefix(prefix, physicaltenants.DefaultID)); err != nil {
		return fmt.Errorf("failed to configure Camunda secret store: %w", err)
	}
	if len(settings.PhysicalTenants) > 0 && settings.PhysicalTenantsEnv == nil {
		settings.PhysicalTenantsEnv = map[string]string{}
	}
	for _, tenant := range settings.PhysicalTenants {
		settings.PhysicalTenantsEnv[physicaltenants.SecretStorePrefixEnv(tenant.ID)] = physicaltenants.SecretVariablePrefix(prefix, tenant.ID)
	}
	return nil
}
