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
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"golang.org/x/sys/windows"
)

func ensureDirectory(path string) (string, error) {
	path, err := filepath.Abs(path)
	if err != nil {
		return "", fmt.Errorf("failed to resolve local secrets directory: %w", err)
	}
	info, err := os.Lstat(path)
	if os.IsNotExist(err) {
		if err := validateExistingDirectoryAncestry(filepath.Dir(path)); err != nil {
			return "", err
		}
		if err := os.MkdirAll(path, 0o700); err != nil {
			return "", fmt.Errorf("failed to create local secrets directory: %w", err)
		}
		info, err = os.Lstat(path)
	}
	if err != nil {
		return "", fmt.Errorf("failed to inspect local secrets directory: %w", err)
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return "", fmt.Errorf("local secrets path %q must be a directory and not a reparse point", path)
	}
	if err := validateDirectoryAncestry(path); err != nil {
		return "", err
	}
	owned, err := ownedByTrustedPrincipal(path)
	if err != nil {
		return "", err
	}
	if !owned {
		return "", fmt.Errorf("local secrets directory %q is not owned by a trusted Windows principal", path)
	}
	if err := restrictAccess(path, true); err != nil {
		return "", err
	}
	entries, err := os.ReadDir(path)
	if err != nil {
		return "", fmt.Errorf("failed to inspect existing Windows secrets: %w", err)
	}
	for _, entry := range entries {
		if !isRuntimeSecretName(entry.Name()) {
			continue
		}
		entryPath := filepath.Join(path, entry.Name())
		info, err := os.Lstat(entryPath)
		if err != nil {
			return "", fmt.Errorf("failed to inspect secret %q: %w", entry.Name(), err)
		}
		if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
			return "", fmt.Errorf("secret %q must be a regular file and not a reparse point", entry.Name())
		}
		if err := restrictAccess(entryPath, false); err != nil {
			return "", err
		}
	}
	return filepath.Clean(path), nil
}

func validateDirectoryAncestry(path string) error {
	volume := filepath.VolumeName(path) + string(filepath.Separator)
	for current := filepath.Clean(path); !strings.EqualFold(current, volume); current = filepath.Dir(current) {
		info, err := os.Lstat(current)
		if err != nil {
			return fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
		}
		if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("local secrets directory ancestor %q must be a directory and not a reparse point", current)
		}
		owned, err := ownedByTrustedPrincipal(current)
		if err != nil {
			return err
		}
		if !owned {
			return fmt.Errorf("local secrets directory ancestor %q is not owned by a trusted Windows principal", current)
		}
	}
	return nil
}

func validateExistingDirectoryAncestry(path string) error {
	for current := filepath.Clean(path); ; current = filepath.Dir(current) {
		_, err := os.Lstat(current)
		if err == nil {
			return validateDirectoryAncestry(current)
		}
		if !os.IsNotExist(err) {
			return fmt.Errorf("failed to inspect local secrets directory ancestry: %w", err)
		}
		if parent := filepath.Dir(current); parent == current {
			return errors.New("failed to locate existing local secrets directory ancestry")
		}
	}
}

func ownedByTrustedPrincipal(path string) (bool, error) {
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.OWNER_SECURITY_INFORMATION)
	if err != nil {
		return false, fmt.Errorf("failed to inspect Windows owner: %w", err)
	}
	owner, _, err := descriptor.Owner()
	if err != nil {
		return false, err
	}
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		return false, err
	}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		return false, err
	}
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	if err != nil {
		return false, err
	}
	return isTrustedPrincipal(owner, user.User.Sid, administrators, system), nil
}

func isTrustedPrincipal(owner, user, administrators, system *windows.SID) bool {
	return owner != nil && (owner.Equals(user) || owner.Equals(administrators) || owner.Equals(system))
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

	if _, err := temporary.Write(value); err != nil {
		return err
	}
	if err := temporary.Sync(); err != nil {
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}

	from, err := windows.UTF16PtrFromString(temporaryPath)
	if err != nil {
		return err
	}
	to, err := windows.UTF16PtrFromString(destination)
	if err != nil {
		return err
	}
	if err := windows.MoveFileEx(from, to, windows.MOVEFILE_REPLACE_EXISTING|windows.MOVEFILE_WRITE_THROUGH); err != nil {
		return err
	}
	return restrictAccess(destination, false)
}

func restrictAccess(path string, inherit bool) error {
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		return fmt.Errorf("failed to identify current Windows user: %w", err)
	}
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	if err != nil {
		return err
	}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		return err
	}
	var pinner runtime.Pinner
	defer pinner.Unpin()
	for _, sid := range []*windows.SID{user.User.Sid, system, administrators} {
		pinner.Pin(sid)
	}
	entries := make([]windows.EXPLICIT_ACCESS, 0, 3)
	var inheritance uint32
	if inherit {
		inheritance = windows.SUB_CONTAINERS_AND_OBJECTS_INHERIT
	}
	for _, sid := range []*windows.SID{user.User.Sid, system, administrators} {
		entries = append(entries, windows.EXPLICIT_ACCESS{
			AccessPermissions: windows.GENERIC_ALL,
			AccessMode:        windows.GRANT_ACCESS,
			Inheritance:       inheritance,
			Trustee: windows.TRUSTEE{
				TrusteeForm:  windows.TRUSTEE_IS_SID,
				TrusteeValue: windows.TrusteeValueFromSID(sid),
			},
		})
	}
	acl, err := windows.ACLFromEntries(entries, nil)
	if err != nil {
		return err
	}
	if err := windows.SetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION, nil, nil, acl, nil); err != nil {
		return fmt.Errorf("failed to secure Windows secret path: %w", err)
	}
	return nil
}
