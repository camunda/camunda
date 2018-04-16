package org.camunda.optimize.upgrade.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * Bunch of utility methods that might be required during upgrade
 * operation.
 *
 * @author Askar Akhmerov
 */
public class SchemaUpgradeUtil {
  public static final String CREATE_SNAPSHOT = "create-snapshot";
  protected static Logger logger = LoggerFactory.getLogger(SchemaUpgradeUtil.class);

  /**
   * @param filePath -
   * @return
   */
  public static String readClasspathFileAsString(String filePath) {
    InputStream inputStream = SchemaUpgradeUtil.class.getClassLoader().getResourceAsStream(filePath);
    String data = null;
    try {
      data = readFromInputStream(inputStream);
    } catch (IOException e) {
      logger.error("can't read [{}] from classpath", filePath, e);
    }
    return data;
  }

  private static String readFromInputStream(InputStream inputStream) throws IOException {
    try(ByteArrayOutputStream result = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }

      return result.toString("UTF-8");
    }
  }
}
