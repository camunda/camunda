//go:build windows

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
	root := os.Getenv("LOCALAPPDATA")
	if root == "" {
		return "", fmt.Errorf("LOCALAPPDATA is not set; set C8RUN_SECRETS_DIR")
	}
	if !filepath.IsAbs(root) {
		return "", fmt.Errorf("LOCALAPPDATA must be an absolute path")
	}
	return filepath.Join(root, "Camunda", "C8Run", DirectoryName), nil
}
