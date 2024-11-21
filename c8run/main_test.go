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
		keystore:         "/tmp/camundatest/certs/secret.jks",
		keystorePassword: "changeme",
	}
	expectedJavaOpts := "JAVA_OPTS= -Dserver.ssl.keystore=file:" + settings.keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.keystorePassword

	javaOpts := adjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()

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
		keystore:         "",
		keystorePassword: "",
	}

	javaOpts := adjustJavaOpts("", settings)
	c8runPlatform := getC8RunPlatform()

	cmd := c8runPlatform.CamundaCmd("8.7.0", "/tmp/camundatest/", "", javaOpts)

	for _, envVar := range cmd.Env {
		if strings.Contains(envVar, "JAVA_OPTS") {
			assert.Fail(t, "JAVA_OPTS should not be set")
		}
	}
}
