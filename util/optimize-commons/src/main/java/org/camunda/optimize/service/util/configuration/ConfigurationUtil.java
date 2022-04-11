/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
    throw new OptimizeConfigurationException(errorMessage);
  }

  /**
   * Checks if the given path to file is a relative to the class path
   * or an absolute and then tries to resolve the corresponding file
   * behind the path to a stream.
   * <p>
   * Note: Make sure to close the stream after it has been used.
   */
  public static InputStream resolvePathToStream(String pathToFile) {
    final File file = new File(pathToFile);

    if (fileExistsAndHavePermissionsToRead(file)) {
      try {
        return new FileInputStream(file);
      } catch (FileNotFoundException e) {
        logger.error("Failed creating URL for file {}", pathToFile, e);
      }
    } else if (existsInClasspath(pathToFile)) {
      return getStreamOfClasspathFile(pathToFile);
    }

    String errorMessage = String.format("Could not find or do not have permissions to read file [%s]!", pathToFile);
    throw new OptimizeConfigurationException(errorMessage);
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

  public static List<InputStream> getLocationsAsInputStream(String[] locationsToUse) {
    List<InputStream> sources = new ArrayList<>();
    for (String location : locationsToUse) {
      InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }
    return sources;
  }

  private static boolean fileExistsAndHavePermissionsToRead(File file) {
    if (file.exists() && !file.isDirectory()) {
      return file.canRead();
    }
    return false;
  }

  private static boolean existsInClasspath(String classpathToFile) {
    return ConfigurationUtil.class.getClassLoader().getResource(classpathToFile) != null;
  }

  private static URL getAbsolutePathOfClasspathFile(String classpathToFile) {
    return Objects.requireNonNull(ConfigurationUtil.class.getClassLoader().getResource(classpathToFile));
  }

  private static InputStream getStreamOfClasspathFile(String classpathToFile) {
    return Objects.requireNonNull(wrapInputStream(classpathToFile));
  }

  private static InputStream wrapInputStream(String location) {
    return ConfigurationUtil.class.getClassLoader().getResourceAsStream(location);
  }
}
