//go:build darwin

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
	root, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("failed to resolve user data directory; set C8RUN_SECRETS_DIR: %w", err)
	}
	return filepath.Join(root, "Camunda", "C8Run", DirectoryName), nil
}
