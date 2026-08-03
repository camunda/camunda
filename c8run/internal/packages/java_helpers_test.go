/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package packages

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestVerifyClassFileVersionAcceptsJava21Class(t *testing.T) {
	classFile := filepath.Join(t.TempDir(), "JavaVersion.class")
	expectedMajor := 44 + helperJavaRelease
	data := []byte{0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, byte(expectedMajor >> 8), byte(expectedMajor)}
	if err := os.WriteFile(classFile, data, 0o644); err != nil {
		t.Fatalf("failed to write class file: %v", err)
	}

	if err := verifyClassFileVersion(classFile); err != nil {
		t.Fatalf("expected no error for Java %d class file, got: %v", helperJavaRelease, err)
	}
}

func TestVerifyClassFileVersionRejectsWrongVersion(t *testing.T) {
	classFile := filepath.Join(t.TempDir(), "JavaVersion.class")
	wrongMajor := 44 + helperJavaRelease + 4
	data := []byte{0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, byte(wrongMajor >> 8), byte(wrongMajor)}
	if err := os.WriteFile(classFile, data, 0o644); err != nil {
		t.Fatalf("failed to write class file: %v", err)
	}

	err := verifyClassFileVersion(classFile)
	if err == nil {
		t.Fatalf("expected error for Java %d class file, got nil", helperJavaRelease+4)
	}
	if !strings.Contains(err.Error(), "expected Java 21") {
		t.Fatalf("expected error to mention 'expected Java 21', got: %v", err)
	}
}
