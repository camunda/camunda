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
	"strings"
	"syscall"

	"golang.org/x/sys/unix"
)

func ensureDirectory(path string) (string, error) {
	if info, err := os.Lstat(path); err == nil && info.Mode()&os.ModeSymlink != 0 {
		return "", fmt.Errorf("local secrets path %q must be a directory and not a symbolic link", path)
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return "", fmt.Errorf("failed to inspect local secrets directory: %w", err)
	}
	resolvedPath, err := resolveExistingSymlinks(path)
	if err != nil {
		return "", err
	}
	if err := createDirectoryTree(resolvedPath); err != nil {
		return "", err
	}
	info, err := os.Lstat(resolvedPath)
	if err != nil {
		return "", fmt.Errorf("failed to inspect local secrets directory: %w", err)
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return "", fmt.Errorf("local secrets path %q must be a directory and not a symbolic link", resolvedPath)
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); ok && stat.Uid != uint32(os.Geteuid()) {
		return "", fmt.Errorf("local secrets directory %q is not owned by the current user", resolvedPath)
	}
	if err := os.Chmod(resolvedPath, 0o700); err != nil {
		return "", fmt.Errorf("failed to secure local secrets directory: %w", err)
	}
	entries, err := os.ReadDir(resolvedPath)
	if err != nil {
		return "", fmt.Errorf("failed to inspect existing local secrets: %w", err)
	}
	for _, entry := range entries {
		if !isRuntimeSecretName(entry.Name()) {
			continue
		}
		entryPath := filepath.Join(resolvedPath, entry.Name())
		info, err := os.Lstat(entryPath)
		if err != nil {
			return "", fmt.Errorf("failed to inspect secret %q: %w", entry.Name(), err)
		}
		if !info.Mode().IsRegular() {
			return "", fmt.Errorf("secret %q must be a regular file and not a symbolic link", entry.Name())
		}
		if err := os.Chmod(entryPath, 0o600); err != nil {
			return "", fmt.Errorf("failed to secure secret %q: %w", entry.Name(), err)
		}
	}
	return resolvedPath, nil
}

func resolveExistingSymlinks(path string) (string, error) {
	missing := []string{}
	existing := filepath.Clean(path)
	for {
		_, err := os.Lstat(existing)
		if err == nil {
			break
		}
		if !errors.Is(err, os.ErrNotExist) {
			return "", fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
		}
		parent := filepath.Dir(existing)
		if parent == existing {
			return "", fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
		}
		missing = append(missing, filepath.Base(existing))
		existing = parent
	}
	resolved, err := filepath.EvalSymlinks(existing)
	if err != nil {
		return "", fmt.Errorf("failed to resolve local secrets directory ancestry: %w", err)
	}
	for index := len(missing) - 1; index >= 0; index-- {
		resolved = filepath.Join(resolved, missing[index])
	}
	return resolved, nil
}

func createDirectoryTree(path string) error {
	if !filepath.IsAbs(path) {
		return fmt.Errorf("local secrets path %q must be absolute", path)
	}
	current, err := unix.Open(string(filepath.Separator), unix.O_RDONLY|unix.O_DIRECTORY|unix.O_CLOEXEC, 0)
	if err != nil {
		return fmt.Errorf("failed to open local secrets directory root: %w", err)
	}
	defer func() { _ = unix.Close(current) }()

	components := strings.Split(strings.TrimPrefix(filepath.Clean(path), string(filepath.Separator)), string(filepath.Separator))
	for index, component := range components {
		if component == "" {
			continue
		}
		next, err := unix.Openat(current, component, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_CLOEXEC|unix.O_NOFOLLOW, 0)
		if errors.Is(err, unix.ENOENT) {
			if err := unix.Mkdirat(current, component, 0o700); err != nil && !errors.Is(err, unix.EEXIST) {
				return fmt.Errorf("failed to create local secrets directory: %w", err)
			}
			next, err = unix.Openat(current, component, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_CLOEXEC|unix.O_NOFOLLOW, 0)
		}
		if err != nil {
			return fmt.Errorf("failed to open local secrets directory component %q: %w", component, err)
		}
		if err := validateDirectoryHandle(next, index == len(components)-1); err != nil {
			_ = unix.Close(next)
			return err
		}
		_ = unix.Close(current)
		current = next
	}
	return nil
}

func validateDirectoryHandle(descriptor int, final bool) error {
	var stat unix.Stat_t
	if err := unix.Fstat(descriptor, &stat); err != nil {
		return fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
	}
	if stat.Uid != 0 && stat.Uid != uint32(os.Geteuid()) {
		return errors.New("local secrets directory ancestry is not owned by the current user or root")
	}
	permissions := os.FileMode(stat.Mode).Perm()
	stickyRootDirectory := stat.Uid == 0 && stat.Mode&unix.S_ISVTX != 0
	if !final && permissions&0o022 != 0 && !stickyRootDirectory {
		return errors.New("local secrets directory ancestry must not be writable by other users")
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
	defer func() { _ = directory.Close() }()
	return directory.Sync()
}
