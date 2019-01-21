package org.camunda.optimize.upgrade.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Bunch of utility methods that might be required during upgrade
 * operation.
 */
public class SchemaUpgradeUtil {
  protected static Logger logger = LoggerFactory.getLogger(SchemaUpgradeUtil.class);

  public static Map getDefaultReportConfigurationAsMap() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-report-configuration.json";
    String reportConfigurationStructureAsJson = SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
    ObjectMapper objectMapper = new ObjectMapper();
    Map reportConfigurationAsMap;
    try {
      reportConfigurationAsMap = objectMapper.readValue(reportConfigurationStructureAsJson, Map.class);
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Could not deserialize default report configuration structure as json!");
    }
    return reportConfigurationAsMap;
  }

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
