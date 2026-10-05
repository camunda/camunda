/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package physicaltenants

import (
	"path/filepath"
	"testing"
	"unsafe"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/sys/windows"
)

// The tenants file holds tenant passwords, so on Windows it must carry a protected DACL that
// grants only the current user, SYSTEM and Administrators.
func TestTenantsFileWithPasswordsHasOwnerOnlyWindowsACL(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "hr", Username: "alice"}}, map[string]string{"hr": "pw"}))

	descriptor, err := windows.GetNamedSecurityInfo(store.Path(), windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	require.NoError(t, err)
	control, _, err := descriptor.Control()
	require.NoError(t, err)
	assert.NotZero(t, control&windows.SE_DACL_PROTECTED, "the DACL must not inherit from the parent directory")
	dacl, _, err := descriptor.DACL()
	require.NoError(t, err)

	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	require.NoError(t, err)
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	require.NoError(t, err)
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	require.NoError(t, err)
	allowed := map[string]bool{user.User.Sid.String(): true, administrators.String(): true, system.String(): true}
	require.NotZero(t, dacl.AceCount)
	for index := uint16(0); index < dacl.AceCount; index++ {
		var ace *windows.ACCESS_ALLOWED_ACE
		require.NoError(t, windows.GetAce(dacl, uint32(index), &ace))
		sid := (*windows.SID)(unsafe.Pointer(&ace.SidStart))
		assert.True(t, allowed[sid.String()], "unexpected principal %s can access the tenants file", sid.String())
	}
}
