/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package overrides

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
	"gopkg.in/yaml.v3"
)

func SetEnvVars() error {
	envVars := map[string]string{
		"CAMUNDA_OPERATE_CSRFPREVENTIONENABLED":  "false",
		"CAMUNDA_OPERATE_IMPORTER_READERBACKOFF": "1000",
		"CAMUNDA_REST_QUERY_ENABLED":             "true",
	}

	for key, value := range envVars {
		currentValue := os.Getenv(key)
		if currentValue != "" {
			continue
		}
		if err := os.Setenv(key, value); err != nil {
			return fmt.Errorf("failed to set environment variable %s: %w", key, err)
		}
	}

	return nil
}

func AdjustJavaOpts(javaOpts string, settings types.C8RunSettings) string {
	protocol := "http"
	if settings.HasKeyStore() {
		javaOpts = javaOpts + " -Dserver.ssl.keystore=file:" + settings.Keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.KeystorePassword
		protocol = "https"
	}
	if settings.Port != 8080 {
		javaOpts = javaOpts + " -Dserver.port=" + strconv.Itoa(settings.Port)
	}
	// as demo is set in the default config, we only add the user settings if they differ
	if settings.Username != "demo" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].username=" + settings.Username
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].name=" + settings.Username
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].email=" + settings.Username + "@example.com"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.defaultRoles.admin.users[0]=" + settings.Username
	}
	if settings.Password != "demo" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].password=" + settings.Password
	}
	if err := os.Setenv("CAMUNDA_OPERATE_ZEEBE_RESTADDRESS", protocol+"://localhost:"+strconv.Itoa(settings.Port)); err != nil {
		log.Error().Err(err).Msg("failed to set CAMUNDA_OPERATE_ZEEBE_RESTADDRESS")
	}
	return javaOpts
}

// SetConnectorsAuthEnvVars provides the bundled Connectors runtime with basic-auth
// credentials for the seeded user when the cluster requires authenticated API access.
//
// Connectors talks to the local Camunda REST/gRPC API as a client. When
// authorizations are enabled (or the API is protected), unauthenticated calls are
// rejected and the Connectors health check never turns green. Passing the seeded
// user's credentials lets Connectors authenticate. When the API is unprotected (the
// default), no credentials are needed and nothing is set. Pre-existing values are
// never overwritten so an explicit user override wins.
func SetConnectorsAuthEnvVars(settings types.C8RunSettings) error {
	if !ConnectorsAuthRequired(settings.ConfigPaths) {
		return nil
	}

	credentials := map[string]string{
		"CAMUNDA_CLIENT_AUTH_USERNAME": settings.Username,
		"CAMUNDA_CLIENT_AUTH_PASSWORD": settings.Password,
	}
	for key, value := range credentials {
		if os.Getenv(key) != "" {
			continue
		}
		if err := os.Setenv(key, value); err != nil {
			return fmt.Errorf("failed to set environment variable %s: %w", key, err)
		}
	}
	return nil
}

// ConnectorsAuthRequired reports whether the bundled Connectors runtime needs
// credentials to reach the local Camunda API, based on the effective configuration.
//
// configPaths is the ordered list of config sources, highest precedence first (the
// user --config override before the bundled default), mirroring how Spring layers
// them at startup. Each security key is resolved independently: the first source that
// defines it wins, so a user override enabling authorizations is honoured even when
// the bundled default leaves the API open.
//
// It returns true when authorizations are enabled or when API protection is on
// (unprotected-api: false). Absent, unreadable, or unparseable sources are ignored;
// when a key is defined nowhere the API is treated as open, matching the default
// C8Run behaviour.
func ConnectorsAuthRequired(configPaths []string) bool {
	authorizationsOn, authorizationsFound := false, false
	// The API is open unless a source explicitly protects it.
	apiUnprotected, apiFound := true, false

	for _, path := range configPaths {
		root, ok := readConfigMap(path)
		if !ok {
			continue
		}
		if !authorizationsFound {
			if authorizations, ok := nestedMap(root, "camunda", "security", "authorizations"); ok {
				if enabled, ok := authorizations["enabled"].(bool); ok {
					authorizationsOn, authorizationsFound = enabled, true
				}
			}
		}
		if !apiFound {
			if authentication, ok := nestedMap(root, "camunda", "security", "authentication"); ok {
				if unprotected, ok := authentication["unprotected-api"].(bool); ok {
					apiUnprotected, apiFound = unprotected, true
				}
			}
		}
		if authorizationsFound && apiFound {
			break
		}
	}

	return authorizationsOn || !apiUnprotected
}

func readConfigMap(path string) (map[string]any, bool) {
	if path == "" {
		return nil, false
	}
	info, err := os.Stat(path)
	if err != nil {
		return nil, false
	}
	if info.IsDir() {
		return readConfigMap(filepath.Join(path, "application.yaml"))
	}
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, false
	}
	var root map[string]any
	if err := yaml.Unmarshal(content, &root); err != nil {
		return nil, false
	}
	return root, true
}

func nestedMap(root map[string]any, keys ...string) (map[string]any, bool) {
	current := root
	for _, key := range keys {
		next, ok := current[key].(map[string]any)
		if !ok {
			return nil, false
		}
		current = next
	}
	return current, true
}
