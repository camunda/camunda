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
	"path/filepath"
	"testing"
	"unsafe"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/sys/windows"
)

func assertRestrictedACL(t *testing.T, path string, inherits bool) {
	t.Helper()
	const fileAllAccess = windows.STANDARD_RIGHTS_REQUIRED | windows.SYNCHRONIZE | 0x1ff
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	require.NoError(t, err)
	control, _, err := descriptor.Control()
	require.NoError(t, err)
	assert.NotZero(t, control&windows.SE_DACL_PROTECTED)
	dacl, _, err := descriptor.DACL()
	require.NoError(t, err)
	require.NotZero(t, dacl.AceCount)

	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	require.NoError(t, err)
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	require.NoError(t, err)
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	require.NoError(t, err)
	expected := map[string]bool{user.User.Sid.String(): false, administrators.String(): false, system.String(): false}
	for index := uint16(0); index < dacl.AceCount; index++ {
		var ace *windows.ACCESS_ALLOWED_ACE
		require.NoError(t, windows.GetAce(dacl, uint32(index), &ace))
		assert.Equal(t, uint8(windows.ACCESS_ALLOWED_ACE_TYPE), ace.Header.AceType)
		assert.Zero(t, ace.Header.AceFlags&windows.INHERITED_ACE)
		assert.Contains(t, []windows.ACCESS_MASK{windows.GENERIC_ALL, fileAllAccess}, ace.Mask)
		sid := (*windows.SID)(unsafe.Pointer(&ace.SidStart))
		_, trusted := expected[sid.String()]
		assert.True(t, trusted, "unexpected ACL principal %s", sid.String())
		expected[sid.String()] = true
	}
	for sid, found := range expected {
		assert.True(t, found, "missing ACL principal %s", sid)
	}
	if !inherits {
		assert.Equal(t, uint16(3), dacl.AceCount)
	}
}

func TestWriteOwnerOnlyFileRestrictsWindowsACL(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "physical-tenants.yaml")
	require.NoError(t, WriteOwnerOnlyFile(path, []byte("passwords:\n  hr: pw\n")))
	assertRestrictedACL(t, path, false)

	// Rewriting keeps the protected ACL.
	require.NoError(t, WriteOwnerOnlyFile(path, []byte("passwords: {}\n")))
	assertRestrictedACL(t, path, false)
}
