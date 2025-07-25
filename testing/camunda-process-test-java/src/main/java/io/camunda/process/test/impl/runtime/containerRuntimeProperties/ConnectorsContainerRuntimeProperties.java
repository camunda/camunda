/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime.containerRuntimeProperties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyMapOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.VersionedPropertiesUtil.getLatestReleasedVersion;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.util.Map;
import java.util.Properties;

public class ConnectorsContainerRuntimeProperties {
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME =
      "connectors.dockerImageName";
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION =
      "connectors.dockerImageVersion";
  public static final String PROPERTY_NAME_CONNECTORS_ENABLED = "connectorsEnabled";
  public static final String PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX = "connectorsEnvVars.";
  public static final String PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX = "connectorsSecrets.";

  private final boolean isConnectorsEnabled;
  private final String connectorsDockerImageName;
  private final String connectorsDockerImageVersion;
  private final Map<String, String> connectorsEnvVars;
  private final Map<String, String> connectorsSecrets;

  public ConnectorsContainerRuntimeProperties(final Properties properties) {
    // connectors are disabled by default
    isConnectorsEnabled =
        getPropertyOrDefault(properties, PROPERTY_NAME_CONNECTORS_ENABLED, "false")
            .trim()
            .equalsIgnoreCase("true");
    connectorsDockerImageName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_NAME);
    connectorsDockerImageVersion =
        getLatestReleasedVersion(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_VERSION);
    connectorsEnvVars = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX);
    connectorsSecrets = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX);
  }

  public boolean isConnectorsEnabled() {
    return isConnectorsEnabled;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsEnvVars;
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsSecrets;
  }
}
