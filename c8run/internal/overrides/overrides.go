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
	"github.com/camunda/camunda/c8run/internal/springconfig"
	"os"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
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

	// JVM options and environment variables beat config files in Spring, so read them first.
	if value, ok := effectiveOverride("camunda.security.authorizations.enabled",
		"CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED"); ok {
		authorizationsOn, authorizationsFound = value, true
	}
	if value, ok := effectiveOverride("camunda.security.authentication.unprotected-api",
		"CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API"); ok {
		apiUnprotected, apiFound = value, true
	}

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

// effectiveOverride reads a boolean from JAVA_OPTS (command line, highest), then
// JDK_JAVA_OPTIONS, then the given environment variables. The last -D in a string wins.
func effectiveOverride(property string, envNames ...string) (bool, bool) {
	camel := strings.Replace(property, "unprotected-api", "unprotectedApi", 1)
	for _, source := range []string{"JAVA_OPTS", "JDK_JAVA_OPTIONS"} {
		value, found := "", false
		for _, opt := range strings.Fields(os.Getenv(source)) {
			for _, name := range []string{property, camel} {
				if prefix := "-D" + name + "="; strings.HasPrefix(opt, prefix) {
					value, found = strings.TrimPrefix(opt, prefix), true
				}
			}
		}
		if found {
			if parsed, err := strconv.ParseBool(strings.TrimSpace(value)); err == nil {
				return parsed, true
			}
		}
	}
	for _, name := range envNames {
		if parsed, err := strconv.ParseBool(strings.TrimSpace(os.Getenv(name))); err == nil {
			return parsed, true
		}
	}
	return false, false
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
		return readConfigMap(springconfig.FilesIn(path)[0])
	}
	return springconfig.Load(path)
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
