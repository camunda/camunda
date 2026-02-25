/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"github.com/camunda/camunda/c8run/internal/overrides"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"strings"
	"testing"
)

func TestCamundaCmdWithKeystoreSettings(t *testing.T) {

	settings := types.C8RunSettings{
		Config:           "",
		Detached:         false,
		Port:             8080,
		Keystore:         "/tmp/camundatest/certs/secret.jks",
		KeystorePassword: "changeme",
	}
	expectedJavaOpts := "JAVA_OPTS= -Dserver.ssl.keystore=file:" + settings.Keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.KeystorePassword + " -Dspring.profiles.active=operate,tasklist,broker"

	javaOpts := overrides.AdjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()
	err := validateKeystore(settings, "/tmp/camundatest/")
	assert.Nil(t, err)

	cmd := c8runPlatform.CamundaCmd("8.7.0", "/tmp/camundatest/", "", javaOpts)

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

	cmd := c8runPlatform.CamundaCmd("8.7.0", "/tmp/camundatest/", "", javaOpts)

	javaOptsEnvVar := ""
	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			javaOptsEnvVar = envVar
			break
		}
	}
	assert.Contains(t, javaOptsEnvVar, "-Dserver.port=8087")

}

func TestDockerCommandEnvOverridesVersion(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "8.7.99")
	base := []string{"CAMUNDA_VERSION=8.7.23", "FOO=bar"}

	result := dockerCommandEnv(base)

	assert.Contains(t, result, "CAMUNDA_VERSION=8.7.99")
	assert.Equal(t, []string{"CAMUNDA_VERSION=8.7.23", "FOO=bar"}, base)
}

func TestDockerCommandEnvAddsVersionWhenMissing(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "8.7.99")
	base := []string{"FOO=bar"}

	result := dockerCommandEnv(base)

	assert.Contains(t, result, "CAMUNDA_VERSION=8.7.99")
	assert.Equal(t, []string{"FOO=bar"}, base)
}

func TestDockerCommandEnvNoOverrideWithoutDockerVersion(t *testing.T) {
	t.Setenv("CAMUNDA_DOCKER_VERSION", "")
	base := []string{"CAMUNDA_VERSION=8.7.23"}

	result := dockerCommandEnv(base)

	assert.Equal(t, base, result)
}
