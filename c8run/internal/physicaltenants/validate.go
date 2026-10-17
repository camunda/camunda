/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Package physicaltenants manages the physical tenants c8run starts next to the implicit
// "default" tenant: persistence, validation, config generation, and the startup summary.
package physicaltenants

import (
	"fmt"
	"strconv"
	"strings"
)

const (
	// DefaultID is the implicit tenant built from the top-level camunda.* config.
	DefaultID = "default"
	// MaxIDLength mirrors the engine's limit for physical tenant ids.
	MaxIDLength = 64
	// MinCamundaMinor is the first 8.x minor that supports physical tenants.
	MinCamundaMinor = 10
)

// ValidateID checks an id against the engine's rule ([a-z0-9]{1,64}, not "default") and
// returns an actionable error that suggests a valid alternative where one exists.
func ValidateID(id string) error {
	if id == "" {
		return fmt.Errorf("physical tenant ID must not be empty")
	}
	if id == DefaultID {
		return fmt.Errorf("%q is reserved: the default physical tenant always exists and is served at the unprefixed URLs", DefaultID)
	}
	if len(id) > MaxIDLength {
		return fmt.Errorf("physical tenant ID %q is %d characters; the maximum is %d", id, len(id), MaxIDLength)
	}
	for _, r := range id {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			continue
		}
		if s := SuggestID(id); s != "" && s != DefaultID {
			return fmt.Errorf("%q is not a valid physical tenant ID: use lowercase letters and digits only (try %q)", id, s)
		}
		return fmt.Errorf("%q is not a valid physical tenant ID: use lowercase letters and digits only", id)
	}
	return nil
}

// MaxRDBMSIDLength bounds ids on RDBMS secondary storage. The tenant's table and index names
// are "<ID>_" + the schema name; the longest schema name is 54 characters
// (IDX_AGENT_INSTANCE_ELEMENT_INSTANCE_AGENT_INSTANCE_KEY) and PostgreSQL truncates
// identifiers at 63, so 54 + 1 + 8 = 63. Longer ids would collide after truncation.
const MaxRDBMSIDLength = 8

// usesRDBMSPrefix reports whether the storage type isolates tenants with an RDBMS table
// prefix; it mirrors StoragePrefix, where rdbms and an unset type share the RDBMS branch.
func usesRDBMSPrefix(storageType string) bool {
	switch strings.ToLower(strings.TrimSpace(storageType)) {
	case "elasticsearch", "opensearch", "none":
		return false
	}
	return true
}

// ValidateIDForStorage applies ValidateID plus the limits of the secondary storage the
// tenant's data lands in, so ids that would break schema creation are never accepted.
func ValidateIDForStorage(id, storageType string) error {
	if err := ValidateID(id); err != nil {
		return err
	}
	if usesRDBMSPrefix(storageType) && len(id) > MaxRDBMSIDLength {
		return fmt.Errorf("physical tenant ID %q is too long for RDBMS secondary storage: IDs can be at most %d characters (try %q)", id, MaxRDBMSIDLength, id[:MaxRDBMSIDLength])
	}
	return nil
}

// SuggestID lowercases and strips everything that is not [a-z0-9].
func SuggestID(id string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(id) {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	s := b.String()
	if len(s) > MaxIDLength {
		s = s[:MaxIDLength]
	}
	return s
}

// ParseIDList splits repeatable, comma-separated flag values, validates and de-duplicates them.
func ParseIDList(values []string) ([]string, error) {
	var ids []string
	seen := map[string]bool{}
	for _, value := range values {
		for _, part := range strings.Split(value, ",") {
			id := strings.TrimSpace(part)
			if id == "" {
				continue
			}
			if err := ValidateID(id); err != nil {
				return nil, err
			}
			if seen[id] {
				return nil, fmt.Errorf("physical tenant %q is listed more than once", id)
			}
			seen[id] = true
			ids = append(ids, id)
		}
	}
	return ids, nil
}

// SupportsVersion reports whether a Camunda version supports physical tenants. Unparseable
// versions (snapshots, custom builds) are allowed so local development is never blocked.
func SupportsVersion(version string) bool {
	version = strings.TrimPrefix(strings.TrimSpace(version), "v")
	parts := strings.SplitN(version, ".", 3)
	if len(parts) < 2 {
		return true
	}
	major, err1 := strconv.Atoi(parts[0])
	minor, err2 := strconv.Atoi(strings.SplitN(parts[1], "-", 2)[0])
	if err1 != nil || err2 != nil {
		return true
	}
	return major > 8 || (major == 8 && minor >= MinCamundaMinor)
}
