/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationUtil {

  private static final Logger logger = LoggerFactory.getLogger(ConfigurationUtil.class);

  private ConfigurationUtil() {}

  public static String cutTrailingSlash(String string) {
    if (string != null && !string.isEmpty() && string.endsWith("/")) {
      string = string.substring(0, string.length() - 1);
    }
    return string;
  }

  /**
   * Checks if the given file exists and Optimize has the rights to read. If the given path is
   * relative to the classpath, it is resolved to an absolute path.
   */
  public static URL resolvePathAsAbsoluteUrl(final String pathToFile) {
    final File file = new File(pathToFile);

    if (fileExistsAndHavePermissionsToRead(file)) {
      try {
        return file.toURI().toURL();
      } catch (final MalformedURLException e) {
        logger.error("Failed creating URL for file {}", pathToFile, e);
      }
    } else if (existsInClasspath(pathToFile)) {
      return getAbsolutePathOfClasspathFile(pathToFile);
    }

    final String errorMessage =
        String.format("Could not find or do not have permissions to read file [%s]!", pathToFile);
    throw new OptimizeConfigurationException(errorMessage);
  }

  /**
   * Checks if the given path to file is a relative to the class path or an absolute and then tries
   * to resolve the corresponding file behind the path to a stream.
   *
   * <p>Note: Make sure to close the stream after it has been used.
   */
  public static InputStream resolvePathToStream(final String pathToFile) {
    final File file = new File(pathToFile);

    if (fileExistsAndHavePermissionsToRead(file)) {
      try {
        return new FileInputStream(file);
      } catch (final FileNotFoundException e) {
        logger.error("Failed creating URL for file {}", pathToFile, e);
      }
    } else if (existsInClasspath(pathToFile)) {
      return getStreamOfClasspathFile(pathToFile);
    }

    final String errorMessage =
        String.format("Could not find or do not have permissions to read file [%s]!", pathToFile);
    throw new OptimizeConfigurationException(errorMessage);
  }

  public static void ensureGreaterThanZero(final int value) {
    if (value <= 0) {
      throw new OptimizeRuntimeException(
          "Value should be greater than zero, but was " + value + "!");
    }
  }

  public static void ensureGreaterThanZero(final long value) {
    if (value <= 0) {
      throw new OptimizeRuntimeException(
          "Value should be greater than zero, but was " + value + "!");
    }
  }

  public static List<InputStream> getLocationsAsInputStream(final String[] locationsToUse) {
    final List<InputStream> sources = new ArrayList<>();
    for (final String location : locationsToUse) {
      final InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }
    return sources;
  }

  private static boolean fileExistsAndHavePermissionsToRead(final File file) {
    if (file.exists() && !file.isDirectory()) {
      return file.canRead();
    }
    return false;
  }

  private static boolean existsInClasspath(final String classpathToFile) {
    return ConfigurationUtil.class.getClassLoader().getResource(classpathToFile) != null;
  }

  private static URL getAbsolutePathOfClasspathFile(final String classpathToFile) {
    return Objects.requireNonNull(
        ConfigurationUtil.class.getClassLoader().getResource(classpathToFile));
  }

  private static InputStream getStreamOfClasspathFile(final String classpathToFile) {
    return Objects.requireNonNull(wrapInputStream(classpathToFile));
  }

  private static InputStream wrapInputStream(final String location) {
    return ConfigurationUtil.class.getClassLoader().getResourceAsStream(location);
  }
}
