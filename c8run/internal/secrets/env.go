/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"fmt"
	"os"
	"regexp"
	"strings"
)

// EnvPrefixEnv is the base prefix of the environment variables that hold secrets in env mode.
const EnvPrefixEnv = "C8RUN_SECRETS_ENV_PREFIX"

var envPrefixPattern = regexp.MustCompile(`^[A-Za-z0-9_]+$`)

// reservedEnvPrefixes mirrors EnvVarSecretStore.RESERVED_PREFIXES in secret-store-env, so a
// prefix Camunda would refuse fails before c8run starts any process.
var reservedEnvPrefixes = []string{
	"CAMUNDA_", "ZEEBE_", "OPERATE_", "TASKLIST_", "OPTIMIZE_", "IDENTITY_", "SPRING_", "SERVER_",
	"MANAGEMENT_", "LOGGING_", "JAVA_", "JDK_", "AWS_", "AZURE_", "GOOGLE_", "KUBERNETES_",
}

// EnvBasePrefix returns the validated base prefix for env mode, always ending with "_".
func EnvBasePrefix() (string, error) {
	base := strings.TrimSpace(os.Getenv(EnvPrefixEnv))
	if base == "" {
		return "", fmt.Errorf("%s=env requires %s, for example %s=MYSECRET_", ModeEnv, EnvPrefixEnv, EnvPrefixEnv)
	}
	if !envPrefixPattern.MatchString(base) {
		return "", fmt.Errorf("%s may contain only letters, digits, and underscores", EnvPrefixEnv)
	}
	if !strings.HasSuffix(base, "_") {
		base += "_"
	}
	return base, nil
}

// DefaultTenantEnvPrefix is the prefix of the default physical tenant's secrets.
func DefaultTenantEnvPrefix(base string) string {
	return base + "DEFAULT_"
}

// TenantEnvPrefix is the prefix of a physical tenant's secrets. Tenant IDs are [a-z0-9] and
// never "default", so the trailing "_" keeps every tenant's prefix disjoint from the others.
func TenantEnvPrefix(base, tenantID string) string {
	return base + strings.ToUpper(tenantID) + "_"
}

// ValidateEnvPrefix refuses a prefix that overlaps a platform prefix such as CAMUNDA_, using the
// same case-insensitive check in both directions as Camunda's env secret store.
func ValidateEnvPrefix(prefix string) error {
	upper := strings.ToUpper(prefix)
	for _, reserved := range reservedEnvPrefixes {
		if strings.HasPrefix(upper, reserved) || strings.HasPrefix(reserved, upper) {
			return fmt.Errorf("secret prefix %q overlaps the reserved prefix %q; choose another %s", prefix, reserved, EnvPrefixEnv)
		}
	}
	return nil
}

// ScrubEnvSecrets removes every variable under base except those under keep, so a connectors
// runtime only sees its own tenant's secrets. Matching is case-insensitive because Windows
// environment names are.
func ScrubEnvSecrets(env []string, base, keep string) []string {
	if base == "" {
		return env
	}
	upperBase, upperKeep := strings.ToUpper(base), strings.ToUpper(keep)
	out := make([]string, 0, len(env))
	for _, kv := range env {
		key, _, _ := strings.Cut(kv, "=")
		upperKey := strings.ToUpper(key)
		if strings.HasPrefix(upperKey, upperBase) && (keep == "" || !strings.HasPrefix(upperKey, upperKeep)) {
			continue
		}
		out = append(out, kv)
	}
	return out
}
