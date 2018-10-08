package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.exceptions.OptimizeValidationException;

import java.io.File;
import java.net.URL;
import java.util.Objects;

public class ConfigurationUtil {

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
  public static String resolvePath(String pathToFile) {
    if (fileExistsAndHavePermissionsToRead(pathToFile)) {
      return pathToFile;
    } else if (existsInClasspath(pathToFile)) {
      return getAbsolutePathOfClasspathFile(pathToFile);
    }
    String errorMessage =
      String.format("Could not find or do not have permissions to read file [%s]!", pathToFile);
    throw new OptimizeValidationException(errorMessage);
  }

  private static boolean fileExistsAndHavePermissionsToRead(String pathToFile) {
    File file = new File(pathToFile);
    if (file.exists() && !file.isDirectory()) {
      return file.canRead();
    }
    return false;
  }

  private static boolean existsInClasspath(String classpathToFile) {
    return ConfigurationUtil
      .class
      .getClassLoader()
      .getResource(classpathToFile) != null;
  }

  private static String getAbsolutePathOfClasspathFile(String classpathToFile) {
    URL fileUrl = Objects.requireNonNull(
      ConfigurationUtil.class
        .getClassLoader()
        .getResource(classpathToFile));
    return fileUrl.toExternalForm();
  }
}
