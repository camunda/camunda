/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ContainerVersionsUtil {

  public static final String ZEEBE_VERSIONS_PROPERTY_NAME = "zeebe.versions";
  public static final String ZEEBE_CURRENTVERSION_PROPERTY_NAME = "zeebe.currentVersion";
  public static final String IDENTITY_CURRENTVERSION_DOCKER_PROPERTY_NAME =
      "identity.currentVersion";
  public static final String VERSIONS_DELIMITER = ",";
  private static final String VERSIONS_FILE = "/container-versions.properties";

  public static String readProperty(final String propertyName) {
    try (final InputStream propsFile =
        ContainerVersionsUtil.class.getResourceAsStream(VERSIONS_FILE)) {
      final Properties props = new Properties();
      props.load(propsFile);
      return props.getProperty(propertyName);
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Unable to read the list of supported Zeebe zeebeVersions.", e);
    }
  }
}
