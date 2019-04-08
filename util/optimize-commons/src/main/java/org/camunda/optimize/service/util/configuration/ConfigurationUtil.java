/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class ConfigurationUtil {
  private static final Logger logger = LoggerFactory.getLogger(ConfigurationUtil.class);

  public static String cutTrailingSlash(String string) {
    if (string != null && !string.isEmpty() && string.endsWith("/")) {
      string = string.substring(0, string.length() - 1);
    }
    return string;
  }

  /**
   * Checks if the given file exists and Optimize has the
   * rights to read. If the given path is relative to
   * the classpath, it is resolved to an absolute path.
   */
  public static URL resolvePathAsAbsoluteUrl(String pathToFile) {
    final File file = new File(pathToFile);

    if (fileExistsAndHavePermissionsToRead(file)) {
      try {
        return file.toURI().toURL();
      } catch (MalformedURLException e) {
        logger.error("Failed creating URL for file {}", pathToFile, e);
      }
    } else if (existsInClasspath(pathToFile)) {
      return getAbsolutePathOfClasspathFile(pathToFile);
    }

    String errorMessage = String.format("Could not find or do not have permissions to read file [%s]!", pathToFile);
    throw new OptimizeRuntimeException(errorMessage);
  }

  private static boolean fileExistsAndHavePermissionsToRead(File file) {
    if (file.exists() && !file.isDirectory()) {
      return file.canRead();
    }
    return false;
  }

  public static void ensureGreaterThanZero(int value) {
    if (value <= 0) {
      throw new OptimizeRuntimeException("Value should be greater than zero, but was " + value + "!");
    }
  }

  public static void ensureGreaterThanZero(long value) {
    if (value <= 0) {
      throw new OptimizeRuntimeException("Value should be greater than zero, but was " + value + "!");
    }
  }

  private static boolean existsInClasspath(String classpathToFile) {
    return ConfigurationUtil.class.getClassLoader().getResource(classpathToFile) != null;
  }

  private static URL getAbsolutePathOfClasspathFile(String classpathToFile) {
    return Objects.requireNonNull(ConfigurationUtil.class.getClassLoader().getResource(classpathToFile));
  }
}
