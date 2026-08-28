//go:build !windows

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
	"syscall"
)

func diagnoseParentDirectory(path string) []string {
	parent := filepath.Dir(path)
	for {
		info, err := os.Lstat(parent)
		if err == nil {
			if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
				return []string{fmt.Sprintf("secrets parent is not a real directory: %s", parent)}
			}
			stat, _ := info.Sys().(*syscall.Stat_t)
			owned := stat != nil && stat.Uid == uint32(os.Geteuid())
			sticky := info.Mode()&os.ModeSticky != 0
			if !owned && !sticky {
				return []string{fmt.Sprintf("secrets parent is not owned by the current user: %s", parent)}
			}
			if info.Mode().Perm()&0o022 != 0 && !sticky {
				return []string{fmt.Sprintf("secrets parent is writable by other users: %s", parent)}
			}
			return nil
		}
		next := filepath.Dir(parent)
		if next == parent {
			return []string{"cannot find an existing secrets parent directory"}
		}
		parent = next
	}
}

func diagnosePermissions(path string, info os.FileInfo) []string {
	expected := os.FileMode(0o600)
	if info.IsDir() {
		expected = 0o700
	}
	var problems []string
	if info.Mode().Perm() != expected {
		problems = append(problems, fmt.Sprintf("unsafe permissions on %s: %04o, expected %04o", path, info.Mode().Perm(), expected))
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); ok && stat.Uid != uint32(os.Geteuid()) {
		problems = append(problems, fmt.Sprintf("path is not owned by the current user: %s", path))
	}
	return problems
}
