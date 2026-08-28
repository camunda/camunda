/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type Diagnosis struct {
	Directory   string
	Exists      bool
	SecretCount int
	Problems    []string
	Warnings    []string
}

func Diagnose(baseDir string) Diagnosis {
	diagnosis := Diagnosis{}
	directory, err := ResolveDirectory(baseDir)
	if err != nil {
		diagnosis.Problems = append(diagnosis.Problems, err.Error())
		return diagnosis
	}
	diagnosis.Directory = directory
	info, err := os.Lstat(directory)
	if errors.Is(err, os.ErrNotExist) {
		diagnosis.Problems = append(diagnosis.Problems, diagnoseParentDirectory(directory)...)
		return diagnosis
	}
	if err != nil {
		diagnosis.Problems = append(diagnosis.Problems, "cannot inspect directory")
		return diagnosis
	}
	diagnosis.Exists = true
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		diagnosis.Problems = append(diagnosis.Problems, "path is not a real directory")
		return diagnosis
	}
	diagnosis.Problems = append(diagnosis.Problems, diagnoseParentDirectory(directory)...)
	diagnosis.Problems = append(diagnosis.Problems, diagnosePermissions(directory, info)...)
	entries, err := os.ReadDir(directory)
	if err != nil {
		diagnosis.Problems = append(diagnosis.Problems, "directory is not readable")
		return diagnosis
	}
	seen := make(map[string]string)
	for _, entry := range entries {
		if entry.Name() == ".c8run-secrets.lock" {
			continue
		}
		if strings.HasPrefix(entry.Name(), ".c8run-secret-") {
			diagnosis.Warnings = append(diagnosis.Warnings, fmt.Sprintf("abandoned staging file: %q", entry.Name()))
			continue
		}
		if err := ValidateName(entry.Name()); err != nil {
			diagnosis.Problems = append(diagnosis.Problems, fmt.Sprintf("invalid secret entry: %q", entry.Name()))
			continue
		}
		entryInfo, err := entry.Info()
		if err != nil || !entryInfo.Mode().IsRegular() {
			diagnosis.Problems = append(diagnosis.Problems, fmt.Sprintf("secret entry is not a regular file: %q", entry.Name()))
			continue
		}
		diagnosis.Problems = append(diagnosis.Problems, diagnosePermissions(filepath.Join(directory, entry.Name()), entryInfo)...)
		folded := strings.ToLower(entry.Name())
		if other, exists := seen[folded]; exists {
			diagnosis.Problems = append(diagnosis.Problems, fmt.Sprintf("secret names differ only by case: %q, %q", other, entry.Name()))
		}
		seen[folded] = entry.Name()
		diagnosis.SecretCount++
	}
	return diagnosis
}
