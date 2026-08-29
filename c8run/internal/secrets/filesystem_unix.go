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
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"syscall"
)

func ensureDirectory(path string) error {
	if err := validateDirectoryAncestry(filepath.Dir(path)); err != nil {
		return err
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
		if err := os.Chmod(entryPath, 0o600); err != nil {
			return fmt.Errorf("failed to secure secret %q: %w", entry.Name(), err)
		}
	}
	return nil
}

func validateDirectoryAncestry(path string) error {
	current := filepath.Clean(path)
	for {
		info, err := os.Lstat(current)
		if errors.Is(err, os.ErrNotExist) {
			parent := filepath.Dir(current)
			if parent == current {
				return fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
			}
			current = parent
			continue
		}
		if err != nil {
			return fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
		}
		if info.Mode()&os.ModeSymlink != 0 {
			stat, ok := info.Sys().(*syscall.Stat_t)
			if ok && stat.Uid != 0 && stat.Uid != uint32(os.Geteuid()) {
				return fmt.Errorf("local secrets directory ancestor %q is not owned by the current user or root", current)
			}
			resolved, err := filepath.EvalSymlinks(current)
			if err != nil {
				return fmt.Errorf("failed to resolve local secrets directory ancestry: %w", err)
			}
			if err := validateDirectoryAncestry(resolved); err != nil {
				return err
			}
			current = filepath.Dir(current)
			continue
		}
		if !info.IsDir() {
			return fmt.Errorf("local secrets directory ancestor %q is not a directory", current)
		}
		stat, ok := info.Sys().(*syscall.Stat_t)
		if ok && stat.Uid != 0 && stat.Uid != uint32(os.Geteuid()) {
			return fmt.Errorf("local secrets directory ancestor %q is not owned by the current user or root", current)
		}
		stickyRootDirectory := ok && stat.Uid == 0 && info.Mode()&os.ModeSticky != 0
		if info.Mode().Perm()&0o022 != 0 && !stickyRootDirectory {
			return fmt.Errorf("local secrets directory ancestor %q must not be writable by other users", current)
		}
		parent := filepath.Dir(current)
		if parent == current {
			return nil
		}
		current = parent
	}
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
	defer func() { _ = directory.Close() }()
	return directory.Sync()
}
