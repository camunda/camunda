package org.camunda.optimize.upgrade.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bunch of utility methods that might be required during upgrade
 * operation.
 *
 * @author Askar Akhmerov
 */
public class SchemaUpgradeUtil {
  public static final String CREATE_SNAPSHOT = "create-snapshot";

  /**
   *
   * @param filePath -
   * @return
   */
  public static String readClasspathFileAsString(String filePath) {
    Path path = null;
    try {
      path = Paths.get(
        SchemaUpgradeUtil.class.getClassLoader()
          .getResource(filePath).toURI()
      );
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    byte[] fileBytes = new byte[0];
    try {
      fileBytes = Files.readAllBytes(path);
    } catch (IOException e) {
      e.printStackTrace();
    }
    String data = new String(fileBytes);
    return data;
  }
}
