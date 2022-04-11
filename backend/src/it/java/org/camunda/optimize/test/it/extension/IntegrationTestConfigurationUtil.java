/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.test.util.PropertyUtil;

import java.util.Properties;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationTestConfigurationUtil {

  private static final String DEFAULT_PROPERTIES_PATH = "integration-extensions.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(DEFAULT_PROPERTIES_PATH);

  private static String getDefaultEngineName() {
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

  public static String getZeebeDockerVersion() {
    return PROPERTIES.getProperty("zeebe.docker.version");
  }

  public static String getEngineDateFormat() {
    return PROPERTIES.getProperty("camunda.engine.serialization.date.format");
  }

  public static String getEngineItPluginEndpoint() {
    return getEngineSchemeAndAuthority() + PROPERTIES.getProperty("camunda.engine.it.plugin.endpoint");
  }

  public static String getEngineRestEndpoint() {
    return getEngineSchemeAndAuthority() + getEngineRestPath();
  }

  private static String getEngineSchemeAndAuthority() {
    return "http://" + getEngineHost() + ":" + getEnginePort();
  }

  public static String getEngineRestPath() {
    return PROPERTIES.getProperty("camunda.engine.rest.engines.endpoint");
  }

  public static String getEngineHost() {
    return PROPERTIES.getProperty("camunda.engine.it.host");
  }

  public static String getEnginePort() {
    return PROPERTIES.getProperty("camunda.engine.it.port");
  }

  public static String getEmbeddedOptimizeEndpoint() {
    return "http://localhost:" + System.getProperty("optimizeHttpPort", "8090");
  }

  public static String getSecuredEmbeddedOptimizeEndpoint() {
    return "https://localhost:" + System.getProperty("optimizeHttpsPort", "8091");
  }

  public static String getEmbeddedOptimizeRestApiEndpoint() {
    return getEmbeddedOptimizeEndpoint() + "/api";
  }

  public static ConfigurationService createItConfigurationService() {
    return ConfigurationServiceBuilder.createConfiguration()
      .loadConfigurationFrom("service-config.yaml", "it/it-config.yaml")
      .build();
  }

  public static int getSmtpPort() {
    return Integer.parseInt(System.getProperty("smtpTestPort", "6666"));
  }

  public static int getHttpTimeoutMillis() {
    return Integer.parseInt(System.getProperty("httpTestTimeout", "10000"));
  }

  public static int getElasticsearchMockServerPort() {
    return Integer.parseInt(
      System.getProperty("elasticSearchMockServerPort", "1080")
    );
  }

  public static int getEngineMockServerPort() {
    return Integer.parseInt(System.getProperty("engineMockServerPort", "1090"));
  }

}
