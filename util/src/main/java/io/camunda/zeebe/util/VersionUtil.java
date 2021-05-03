/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;

public final class VersionUtil {

  public static final Logger LOG = Loggers.FILE_LOGGER;

  private static final String VERSION_PROPERTIES_PATH = "/zeebe-util.properties";
  private static final String VERSION_PROPERTY_NAME = "zeebe.version";
  private static final String LAST_VERSION_PROPERTY_NAME = "zeebe.last.version";
  private static final String VERSION_DEV = "development";

  private static String version;
  private static String versionLowerCase;
  private static String lastVersion;

  private VersionUtil() {}

  /** @return the current version or 'development' if none can be determined. */
  public static String getVersion() {
    if (version == null) {
      // read version from file
      version = readProperty(VERSION_PROPERTY_NAME);
      if (version == null) {
        LOG.warn("Version is not found in version file.");
        version = VersionUtil.class.getPackage().getImplementationVersion();
      }

      if (version == null) {
        version = VERSION_DEV;
      }
    }

    return version;
  }

  public static String getVersionLowerCase() {
    if (versionLowerCase == null) {
      versionLowerCase = getVersion().toLowerCase();
    }
    return versionLowerCase;
  }

  /** @return the previous stable version or null if none was found. */
  public static String getPreviousVersion() {
    if (lastVersion == null) {
      lastVersion = readProperty(LAST_VERSION_PROPERTY_NAME);
    }

    return lastVersion;
  }

  private static String readProperty(final String property) {
    try (InputStream lastVersionFileStream =
        VersionUtil.class.getResourceAsStream(VERSION_PROPERTIES_PATH)) {
      final Properties props = new Properties();
      props.load(lastVersionFileStream);

      return props.getProperty(property);
    } catch (IOException e) {
      LOG.error(String.format("Can't read version file: %s", VERSION_PROPERTIES_PATH), e);
    }

    return null;
  }
}
