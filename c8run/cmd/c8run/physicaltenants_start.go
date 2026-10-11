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
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/physicaltenants"
	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

// firstTenantConnectorsPort is the first port handed to a per-tenant connectors runtime;
// the default tenant's connectors keep 8086.
const firstTenantConnectorsPort = 8087

// applyPhysicalTenants resolves the tenants for this start, validates them before Java is
// launched, writes the generated config, and seeds each tenant's login via the environment.
func applyPhysicalTenants(baseDir, camundaVersion string, settings *types.C8RunSettings) error {
	flagIDs, err := physicaltenants.ParseIDList(settings.PhysicalTenantsFlag)
	if err != nil {
		return fmt.Errorf("--physical-tenants: %w", err)
	}
	defaultConfig := filepath.Join(baseDir, "configuration", "application.yaml")
	var userConfigs []string
	externallyDeclared := envDeclaresTenants()
	for _, p := range settings.ConfigPaths {
		if p != defaultConfig {
			userConfigs = append(userConfigs, p)
			externallyDeclared = externallyDeclared || physicaltenants.ConfigDeclaresTenants(p)
		}
	}
	mode, err := physicaltenants.Mode()
	if err != nil {
		return err
	}

	// The saved tenants file is only needed when c8run will actually read saved tenants.
	var store *physicaltenants.Store
	if len(flagIDs) == 0 && mode == "local" && !externallyDeclared {
		path, err := physicaltenants.ResolvePath(baseDir)
		if err != nil {
			// Never silently start without the user's saved tenants.
			return fmt.Errorf("cannot find your saved physical tenants: %w", err)
		}
		store = physicaltenants.NewStore(path)
	}

	res, err := physicaltenants.Resolve(physicaltenants.ResolveInput{
		EnvDeclaresTenants:  envDeclaresTenants(),
		ReservedPorts:       reservedPorts(settings.Port),
		Store:               store,
		FlagIDs:             flagIDs,
		DefaultUsername:     settings.Username,
		DefaultPassword:     settings.Password,
		UserConfigs:         userConfigs,
		ConnectorsEnabled:   !settings.DisableConnectors,
		FirstConnectorsPort: firstTenantConnectorsPort,
		PortFree:            portFree,
	})
	if err != nil {
		return err
	}
	for _, notice := range res.Notices {
		log.Info().Msg(withCLIName(notice, cliName()))
	}
	if len(res.Tenants) > 0 && camundaVersion != "" && !physicaltenants.SupportsVersion(camundaVersion) {
		return fmt.Errorf("physical tenants require Camunda 8.%d or newer, but this c8run bundles Camunda %s. Upgrade c8run, or delete the saved tenants file (`c8run tenants path` shows where) and drop --physical-tenants", physicaltenants.MinCamundaMinor, camundaVersion)
	}
	// Re-checked here because the storage type can change after `c8run tenants add`.
	for _, t := range res.Tenants {
		if err := physicaltenants.ValidateIDForStorage(t.ID, settings.SecondaryStorageType); err != nil {
			return fmt.Errorf("%w. Remove it with `c8run tenants remove %s` or switch secondary storage", err, t.ID)
		}
	}

	content, err := physicaltenants.RenderForPort(res.Tenants, settings.SecondaryStorageType, settings.Port)
	if err != nil {
		return err
	}
	settings.PhysicalTenants = res.Tenants
	// Written by StartCommand only once startup is certain to proceed (after the port check),
	// so a refused second start never changes what `c8run tenants list` reports as active.
	settings.PhysicalTenantsConfig = content
	if len(res.Tenants) > 0 {
		settings.PhysicalTenantsConfigPath = physicaltenants.GeneratedConfigPath(baseDir)
	}
	settings.PhysicalTenantsEnv = physicaltenants.CredentialEnv(res.Tenants)
	if len(res.Tenants) > 0 {
		ids := make([]string, 0, len(res.Tenants))
		for _, t := range res.Tenants {
			ids = append(ids, t.ID)
		}
		log.Info().Str("tenants", "default,"+strings.Join(ids, ",")).Msg("Starting with physical tenants")
	}
	return nil
}

// portFree checks the port on all interfaces, which is where connectors runtimes bind.
func portFree(port int) bool {
	l, err := net.Listen("tcp", ":"+strconv.Itoa(port))
	if err != nil {
		return false
	}
	_ = l.Close()
	return true
}

// envDeclaresTenants reports whether physical tenants are already declared outside c8run,
// through environment variables or JAVA_OPTS system properties.
func envDeclaresTenants() bool {
	for _, kv := range os.Environ() {
		if strings.HasPrefix(strings.ToUpper(kv), physicaltenants.TenantEnvPrefix) {
			return true
		}
	}
	return strings.Contains(os.Getenv("JAVA_OPTS"), "-Dcamunda.physical-tenants.") ||
		strings.Contains(os.Getenv("JDK_JAVA_OPTIONS"), "-Dcamunda.physical-tenants.")
}

// reservedPorts are ports c8run's own processes bind, never handed to a tenant connectors runtime.
func reservedPorts(camundaPort int) map[int]bool {
	return map[int]bool{camundaPort: true, 8086: true, 9600: true, 26500: true, 26501: true, 26502: true}
}

// configureTenantSecretStores gives every physical tenant its own local secret directory, so
// no tenant can resolve another tenant's (or the default tenant's) camunda.secrets.* names.
func configureTenantSecretStores(baseDir string, settings *types.C8RunSettings) error {
	for _, tenant := range settings.PhysicalTenants {
		directory, err := localsecrets.TenantDirectory(baseDir, tenant.ID)
		if err != nil {
			return err
		}
		store := localsecrets.NewInDirectory(directory)
		if err := store.Ensure(); err != nil {
			return fmt.Errorf("failed to prepare secrets for physical tenant %s: %w", tenant.ID, err)
		}
		resolved, err := store.Directory()
		if err != nil {
			return err
		}
		if settings.PhysicalTenantsEnv == nil {
			settings.PhysicalTenantsEnv = map[string]string{}
		}
		settings.PhysicalTenantsEnv[physicaltenants.SecretStoreEnv(tenant.ID)] = resolved
	}
	return nil
}
