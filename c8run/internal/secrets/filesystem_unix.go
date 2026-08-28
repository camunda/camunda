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
	"strings"
	"syscall"
)

func ensureDirectory(path, baseDir string) error {
	anchor := filepath.Clean(baseDir)
	if !strings.HasPrefix(filepath.Clean(path), anchor+string(os.PathSeparator)) {
		anchor = filepath.Dir(path)
	}
	for current := filepath.Dir(path); current != anchor && current != filepath.Dir(current); current = filepath.Dir(current) {
		info, err := os.Lstat(current)
		if err == nil && info.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("secrets path component %q must not be a symbolic link", current)
		}
	}
	parentPath := filepath.Dir(path)
	for {
		_, err := os.Lstat(parentPath)
		if err == nil {
			break
		}
		if !os.IsNotExist(err) {
			return fmt.Errorf("failed to inspect secrets parent directory: %w", err)
		}
		next := filepath.Dir(parentPath)
		if next == parentPath {
			return fmt.Errorf("failed to find an existing secrets parent directory")
		}
		parentPath = next
	}
	parentInfo, err := os.Lstat(parentPath)
	if err != nil {
		return fmt.Errorf("failed to inspect secrets parent directory: %w", err)
	}
	if !parentInfo.IsDir() || parentInfo.Mode()&os.ModeSymlink != 0 {
		return fmt.Errorf("secrets parent must be a directory and not a symbolic link")
	}
	ownedByCurrentUser := false
	if stat, ok := parentInfo.Sys().(*syscall.Stat_t); ok {
		ownedByCurrentUser = stat.Uid == uint32(os.Geteuid())
	}
	stickyDirectory := parentInfo.Mode()&os.ModeSticky != 0 && parentInfo.Sys().(*syscall.Stat_t).Uid == 0
	if !ownedByCurrentUser && !stickyDirectory {
		return fmt.Errorf("secrets parent directory is not owned by the current user")
	}
	if parentInfo.Mode().Perm()&0o022 != 0 && !stickyDirectory {
		return fmt.Errorf("secrets parent directory must not be writable by group or other users")
	}

	info, err := os.Lstat(path)
	if os.IsNotExist(err) {
		if err := os.MkdirAll(path, 0o700); err != nil {
			return fmt.Errorf("failed to create local secrets directory: %w", err)
		}
		info, err = os.Lstat(path)
	}
	if err != nil {
		return fmt.Errorf("failed to inspect local secrets directory: %w", err)
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return fmt.Errorf("local secrets path %q must be a directory and not a symbolic link", path)
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); ok && stat.Uid != uint32(os.Geteuid()) {
		return fmt.Errorf("local secrets directory %q is not owned by the current user", path)
	}
	if err := os.Chmod(path, 0o700); err != nil {
		return fmt.Errorf("failed to secure local secrets directory: %w", err)
	}
	entries, err := os.ReadDir(path)
	if err != nil {
		return fmt.Errorf("failed to inspect existing local secrets: %w", err)
	}
	for _, entry := range entries {
		if ValidateName(entry.Name()) != nil {
			continue
		}
		entryPath := filepath.Join(path, entry.Name())
		info, err := os.Lstat(entryPath)
		if err != nil {
			return fmt.Errorf("failed to inspect secret %q: %w", entry.Name(), err)
		}
		if !info.Mode().IsRegular() {
			return fmt.Errorf("secret %q must be a regular file and not a symbolic link", entry.Name())
		}
		stat, ok := info.Sys().(*syscall.Stat_t)
		if !ok || stat.Uid != uint32(os.Geteuid()) {
			return fmt.Errorf("secret %q is not owned by the current user", entry.Name())
		}
		if stat.Nlink != 1 {
			return fmt.Errorf("secret %q must not be a hard link", entry.Name())
		}
		if err := os.Chmod(entryPath, 0o600); err != nil {
			return fmt.Errorf("failed to secure secret %q: %w", entry.Name(), err)
		}
	}
	return nil
}

func atomicWrite(destination string, value []byte) (err error) {
	temporary, err := os.CreateTemp(filepath.Dir(destination), ".c8run-secret-*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer func() {
		_ = temporary.Close()
		_ = os.Remove(temporaryPath)
	}()

	if err := temporary.Chmod(0o600); err != nil {
		return err
	}
	if _, err := temporary.Write(value); err != nil {
		return err
	}
	if err := temporary.Sync(); err != nil {
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := os.Rename(temporaryPath, destination); err != nil {
		return err
	}
	directory, err := os.Open(filepath.Dir(destination))
	if err != nil {
		return err
	}
	defer directory.Close()
	return directory.Sync()
}
