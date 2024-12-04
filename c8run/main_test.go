/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"github.com/stretchr/testify/assert"
	"strings"
	"testing"
)

func TestCamundaCmdWithKeystoreSettings(t *testing.T) {

	settings := C8RunSettings{
		config:           "",
		detached:         false,
		port:             8080,
		keystore:         "/tmp/camundatest/certs/secret.jks",
		keystorePassword: "changeme",
	}
	expectedJavaOpts := "JAVA_OPTS= -Dserver.ssl.keystore=file:" + settings.keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.keystorePassword

	javaOpts := adjustJavaOpts("", settings)
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

func TestCamundaCmdHasNoJavaOpts(t *testing.T) {

	settings := C8RunSettings{
		config:           "",
		detached:         false,
		port:             8080,
		keystore:         "",
		keystorePassword: "",
	}

	javaOpts := adjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()
	err := validateKeystore(settings, "/tmp/camundatest/")
	assert.Nil(t, err)

	cmd := c8runPlatform.CamundaCmd("8.7.0", "/tmp/camundatest/", "", javaOpts)

	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			assert.Fail(t, "JAVA_OPTS should not be set")
		}
	}
}

func TestCamundaCmdKeystoreRequiresPassword(t *testing.T) {

	settings := C8RunSettings{
		config:           "",
		detached:         false,
		port:             8080,
		keystore:         "/tmp/camundatest/certs/secret.jks",
		keystorePassword: "",
	}
	err := validateKeystore(settings, "/tmp/camundatest/")

	assert.NotNil(t, err)
	assert.Error(t, err, "You must provide a password with --keystorePassword to unlock your keystore.")
}

func TestCamundaCmdDifferentPort(t *testing.T) {

	settings := C8RunSettings{
		port: 8087,
	}
	javaOpts := adjustJavaOpts("", settings)
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
