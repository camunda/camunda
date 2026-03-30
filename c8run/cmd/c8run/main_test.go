/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/camunda/camunda/c8run/internal/overrides"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestCamundaCmdWithKeystoreSettings(t *testing.T) {
	settings := types.C8RunSettings{
		Config:           "",
		Detached:         false,
		Port:             8080,
		Keystore:         "/tmp/camundatest/certs/secret.jks",
		KeystorePassword: "changeme",
		Username:         "demo",
		Password:         "demo",
	}
	expectedJavaOpts := "JAVA_OPTS= -Dserver.ssl.keystore=file:" + settings.Keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.KeystorePassword

	javaOpts := overrides.AdjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()
	err := validateKeystore(settings, "/tmp/camundatest/")
	assert.Nil(t, err)

	cmd := c8runPlatform.CamundaCmd(context.Background(), "8.7.0", "/tmp/camundatest/", "", javaOpts)

	foundVar := ""
	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			foundVar = envVar
			break
		}
	}
	assert.Equal(t, expectedJavaOpts, foundVar)
}

func TestCamundaCmdKeystoreRequiresPassword(t *testing.T) {
	settings := types.C8RunSettings{
		Config:           "",
		Detached:         false,
		Port:             8080,
		Keystore:         "/tmp/camundatest/certs/secret.jks",
		KeystorePassword: "",
	}
	err := validateKeystore(settings, "/tmp/camundatest/")

	assert.NotNil(t, err)
	assert.Error(t, err, "You must provide a password with --keystorePassword to unlock your keystore.")
}

func TestCamundaCmdDifferentPort(t *testing.T) {
	settings := types.C8RunSettings{
		Port: 8087,
	}
	javaOpts := overrides.AdjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()

	cmd := c8runPlatform.CamundaCmd(context.Background(), "8.7.0", "/tmp/camundatest/", "", javaOpts)

	javaOptsEnvVar := ""
	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			javaOptsEnvVar = envVar
			break
		}
	}
	assert.Contains(t, javaOptsEnvVar, "-Dserver.port=8087")
}

func TestCamundaCmdUsername(t *testing.T) {
	settings := types.C8RunSettings{
		Username: "admin",
	}
	javaOpts := overrides.AdjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()

	cmd := c8runPlatform.CamundaCmd(context.Background(), "8.7.0", "/tmp/camundatest/", "", javaOpts)

	javaOptsEnvVar := ""
	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			javaOptsEnvVar = envVar
			break
		}
	}
	assert.Contains(t, javaOptsEnvVar, "-Dcamunda.security.initialization.users[0].username=admin")
}

func TestCamundaCmdPassword(t *testing.T) {
	settings := types.C8RunSettings{
		Password: "changeme",
	}
	javaOpts := overrides.AdjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()

	cmd := c8runPlatform.CamundaCmd(context.Background(), "8.7.0", "/tmp/camundatest/", "", javaOpts)

	javaOptsEnvVar := ""
	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			javaOptsEnvVar = envVar
			break
		}
	}
	assert.Contains(t, javaOptsEnvVar, "-Dcamunda.security.initialization.users[0].password=changeme")
}

func TestApplySecondaryStorageDefaultsDetectsRdbms(t *testing.T) {
	t.Helper()

	tempDir := t.TempDir()
	configDir := filepath.Join(tempDir, "configuration")
	require.NoError(t, os.MkdirAll(configDir, 0o755))

	config := `
camunda:
  data:
    secondary-storage:
      type: rdbms
`
	require.NoError(t, os.WriteFile(filepath.Join(configDir, "application.yaml"), []byte(config), 0o644))

	settings := types.C8RunSettings{}
	applySecondaryStorageDefaults(tempDir, &settings)

	assert.Equal(t, "rdbms", settings.SecondaryStorageType)
	assert.Equal(t, filepath.Join(configDir, "application.yaml"), settings.ResolvedConfigPath)
}

func TestApplySecondaryStorageDefaultsDetectsElasticsearch(t *testing.T) {
	tempDir := t.TempDir()
	configDir := filepath.Join(tempDir, "configuration")
	require.NoError(t, os.MkdirAll(configDir, 0o755))

	config := `
camunda:
  data:
    secondary-storage:
      type: elasticsearch
`
	require.NoError(t, os.WriteFile(filepath.Join(configDir, "application.yaml"), []byte(config), 0o644))

	settings := types.C8RunSettings{}
	applySecondaryStorageDefaults(tempDir, &settings)

	assert.Equal(t, "elasticsearch", settings.SecondaryStorageType)
	assert.Equal(t, filepath.Join(configDir, "application.yaml"), settings.ResolvedConfigPath)
}

func TestValidatePort(t *testing.T) {
	tests := []struct {
		name    string
		port    int
		wantErr bool
	}{
		{name: "valid lower bound", port: 1},
		{name: "valid upper bound", port: 65535},
		{name: "zero", port: 0, wantErr: true},
		{name: "negative", port: -1, wantErr: true},
		{name: "too large", port: 70000, wantErr: true},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			err := validatePort(tt.port)
			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

func TestDockerCommandEnvOverridesVersion(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "8.9-SNAPSHOT")
	base := []string{"CAMUNDA_VERSION=8.9.0-alpha4", "FOO=bar"}

	result := dockerCommandEnv(base)

	assert.Contains(t, result, "CAMUNDA_VERSION=8.9-SNAPSHOT")
	assert.Equal(t, []string{"CAMUNDA_VERSION=8.9.0-alpha4", "FOO=bar"}, base)
}

func TestDockerCommandEnvAddsVersionWhenMissing(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "8.9-SNAPSHOT")
	base := []string{"FOO=bar"}

	result := dockerCommandEnv(base)

	assert.Contains(t, result, "CAMUNDA_VERSION=8.9-SNAPSHOT")
	assert.Equal(t, []string{"FOO=bar"}, base)
}

func TestDockerCommandEnvNoOverrideWithoutDockerVersion(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "")
	base := []string{"CAMUNDA_VERSION=8.9.0-alpha4"}

	result := dockerCommandEnv(base)

	assert.Equal(t, base, result)
}
