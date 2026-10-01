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
	var store *physicaltenants.Store
	if path, err := physicaltenants.ResolvePath(baseDir); err == nil {
		store = physicaltenants.NewStore(path)
	} else if len(flagIDs) == 0 {
		log.Debug().Err(err).Msg("Physical tenants file unavailable; starting without saved tenants")
	}

	defaultConfig := filepath.Join(baseDir, "configuration", "application.yaml")
	var userConfigs []string
	for _, p := range settings.ConfigPaths {
		if p != defaultConfig {
			userConfigs = append(userConfigs, p)
		}
	}

	res, err := physicaltenants.Resolve(physicaltenants.ResolveInput{
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
		log.Info().Msg(notice)
	}
	if len(res.Tenants) > 0 && camundaVersion != "" && !physicaltenants.SupportsVersion(camundaVersion) {
		return fmt.Errorf("physical tenants require Camunda 8.%d or newer, but this c8run bundles Camunda %s. Remove them with `c8run tenants reset` or upgrade c8run", physicaltenants.MinCamundaMinor, camundaVersion)
	}

	path, err := physicaltenants.WriteGeneratedConfigForPort(baseDir, res.Tenants, settings.SecondaryStorageType, settings.Port)
	if err != nil {
		return err
	}
	settings.PhysicalTenants = res.Tenants
	settings.PhysicalTenantsConfigPath = path
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

func portFree(port int) bool {
	l, err := net.Listen("tcp", "localhost:"+strconv.Itoa(port))
	if err != nil {
		return false
	}
	_ = l.Close()
	return true
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
