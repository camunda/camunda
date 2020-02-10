/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;

public final class VersionUtil {

  public static final Logger LOG = Loggers.FILE_LOGGER;

  private static final String VERSION_PROPERTIES_PATH = "/version.properties";
  private static final String VERSION_PROPERTY_NAME = "zeebe.version";
  private static final String VERSION_DEV = "development";

  private static String version;

  public static String getVersion() {
    if (version == null) {
      // read version from file
      try (InputStream versionFileStream =
          VersionUtil.class.getResourceAsStream(VERSION_PROPERTIES_PATH)) {
        final Properties props = new Properties();
        props.load(versionFileStream);
        version = props.getProperty(VERSION_PROPERTY_NAME);
        if (version == null) {
          LOG.warn("Version is not found in version file.");
        }
      } catch (IOException e) {
        LOG.error(String.format("Can't read version file: %s", VERSION_PROPERTIES_PATH), e);
      }
      if (version == null) {
        version = VersionUtil.class.getPackage().getImplementationVersion();
      }
      if (version == null) {
        version = VERSION_DEV;
      }
    }
    return version;
  }
}
