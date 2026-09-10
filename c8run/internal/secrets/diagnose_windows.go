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
	"unsafe"

	"golang.org/x/sys/windows"
)

func diagnoseParentDirectory(path string) []string {
	parent := filepath.Dir(path)
	for {
		info, err := os.Lstat(parent)
		if err == nil {
			if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
				return []string{fmt.Sprintf("secrets parent is not a real directory: %s", parent)}
			}
			return diagnoseWindowsOwner(parent)
		}
		next := filepath.Dir(parent)
		if next == parent {
			return []string{"cannot find an existing secrets parent directory"}
		}
		parent = next
	}
}

func diagnoseWindowsOwner(path string) []string {
	trusted, err := ownedByTrustedPrincipal(path)
	if err != nil || !trusted {
		return []string{fmt.Sprintf("Windows parent is not owned by the current user, Administrators, or SYSTEM: %s", path)}
	}
	return nil
}

func diagnosePermissions(path string, _ os.FileInfo) []string {
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION|windows.OWNER_SECURITY_INFORMATION)
	if err != nil {
		return []string{fmt.Sprintf("cannot inspect Windows permissions on %s", path)}
	}
	control, _, err := descriptor.Control()
	if err != nil || control&windows.SE_DACL_PROTECTED == 0 {
		return []string{fmt.Sprintf("Windows permissions inherit from the parent on %s", path)}
	}
	dacl, present, err := descriptor.DACL()
	if err != nil || !present || dacl == nil {
		return []string{fmt.Sprintf("Windows permissions are missing on %s", path)}
	}
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		return []string{fmt.Sprintf("cannot identify the current Windows user for %s", path)}
	}
	trusted, ownerErr := ownedByTrustedPrincipal(path)
	if ownerErr != nil || !trusted {
		return []string{fmt.Sprintf("Windows path is not owned by the current user, Administrators, or SYSTEM: %s", path)}
	}
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	if err != nil {
		return []string{fmt.Sprintf("cannot validate Windows permissions on %s", path)}
	}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		return []string{fmt.Sprintf("cannot validate Windows permissions on %s", path)}
	}
	for index := uint32(0); index < uint32(dacl.AceCount); index++ {
		var ace *windows.ACCESS_ALLOWED_ACE
		if err := windows.GetAce(dacl, index, &ace); err != nil {
			return []string{fmt.Sprintf("cannot inspect Windows access entry on %s", path)}
		}
		if ace.Header.AceType != windows.ACCESS_ALLOWED_ACE_TYPE {
			continue
		}
		sid := (*windows.SID)(unsafe.Pointer(&ace.SidStart))
		if !sid.Equals(user.User.Sid) && !sid.Equals(system) && !sid.Equals(administrators) {
			return []string{fmt.Sprintf("Windows permissions grant access to another principal on %s", path)}
		}
	}
	return nil
}
