/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.testcontainers.utility.DockerImageName;

public class ContainerVersionsUtil {
  private static final String TASKLIST_CURRENTVERSION_DOCKER_PROPERTY_NAME =
      "tasklist.currentVersion.docker.tag";
  private static final String TASKLIST_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME =
      "tasklist.currentVersion.docker.repo";

  private static final String VERSIONS_FILE = "container-versions.properties";

  public static DockerImageName getTasklistDockerImageName() {
    return DockerImageName.parse(readProperty(TASKLIST_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME))
        .withTag(readProperty(TASKLIST_CURRENTVERSION_DOCKER_PROPERTY_NAME));
  }

  public static String readProperty(final String propertyName) {
    // Read first System properties, to make sure we can override it in CI
    // If not available we default to spring/maven properties
    final String value = System.getProperty(propertyName);
    if (value != null) {
      return value;
    }

    try (final InputStream propsFile =
        ContainerVersionsUtil.class.getClassLoader().getResourceAsStream(VERSIONS_FILE)) {
      final Properties props = new Properties();
      props.load(propsFile);
      return props.getProperty(propertyName);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Unable to read the list of supported Zeebe zeebeVersions.", e);
    }
  }
}
