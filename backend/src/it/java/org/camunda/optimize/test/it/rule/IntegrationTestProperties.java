/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.rule;

import lombok.experimental.UtilityClass;
import org.camunda.optimize.test.util.PropertyUtil;

import java.util.Properties;

@UtilityClass
public class IntegrationTestProperties {
  private static final String DEFAULT_PROPERTIES_PATH = "integration-rules.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(DEFAULT_PROPERTIES_PATH);

  public static String getDefaultEngineName() {
    return PROPERTIES.getProperty("camunda.optimize.engine.default.name");
  }

  public static String resolveFullDefaultEngineName() {
    return System.getProperty("prefixedDefaultEngineName", resolveFullEngineName(getDefaultEngineName()));
  }

  public static String resolveFullEngineName(final String engineName) {
    return System.getProperty("enginePrefix", "") + engineName;
  }

  public static String getEngineVersion() {
    return PROPERTIES.getProperty("camunda.engine.version");
  }

  public static String getEngineDateFormat() {
    return PROPERTIES.getProperty("camunda.engine.serialization.date.format");
  }

  public static String getEngineItPluginEndpoint() {
    return PROPERTIES.getProperty("camunda.engine.it.plugin.endpoint");
  }

  public static String getEnginesRestEndpoint() {
    return PROPERTIES.getProperty("camunda.engine.rest.engines.endpoint");
  }

  public static String getEmbeddedOptimizeEndpoint() {
    return PROPERTIES.getProperty("camunda.optimize.test.embedded-optimize");
  }

  public static String getEmbeddedOptimizeRestApiEndpoint() {
    return getEmbeddedOptimizeEndpoint() + "/api";
  }
}
