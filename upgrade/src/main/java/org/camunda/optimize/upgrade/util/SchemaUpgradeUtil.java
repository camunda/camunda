/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.common.Strings;
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

  public static String createMappingStringFromMapping(final TypeMappingCreator type) {
    return "{ \"mappings\": {\"" + type.getType() + "\": " + Strings.toString(type.getSource()) + "} }";
  }

  public static Map getDefaultSingleReportConfigurationAsMap() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-single-report-configuration.json";
    return getDefaultReportConfigurationAsMap(pathToMapping);
  }

  public static Map getDefaultCombinedReportConfigurationAsMap() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-combined-report-configuration.json";
    return getDefaultReportConfigurationAsMap(pathToMapping);
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

  private static Map getDefaultReportConfigurationAsMap(String pathToMapping) {
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
