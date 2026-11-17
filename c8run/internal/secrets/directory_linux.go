//go:build linux

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
	"path/filepath"
)

func defaultDirectory() (string, error) {
	if dataHome := os.Getenv("XDG_DATA_HOME"); dataHome != "" {
		if !filepath.IsAbs(dataHome) {
			return "", fmt.Errorf("XDG_DATA_HOME must be an absolute path")
		}
		return filepath.Join(dataHome, "camunda", "c8run", DirectoryName), nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("failed to resolve user data directory; set C8RUN_SECRETS_DIR: %w", err)
	}
	return filepath.Join(home, ".local", "share", "camunda", "c8run", DirectoryName), nil
}
