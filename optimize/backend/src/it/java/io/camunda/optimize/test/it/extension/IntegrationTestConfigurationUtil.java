/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import static io.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.JettyConfig;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import io.camunda.optimize.test.util.PropertyUtil;
import java.util.Properties;
import org.springframework.context.ApplicationContext;

public class IntegrationTestConfigurationUtil {

  private static final String DEFAULT_PROPERTIES_PATH = "integration-extensions.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(DEFAULT_PROPERTIES_PATH);

  private IntegrationTestConfigurationUtil() {}

  private static String getDefaultEngineName() {
    return PROPERTIES.getProperty("camunda.optimize.engine.default.name");
  }

  public static String resolveFullDefaultEngineName() {
    return System.getProperty(
        "prefixedDefaultEngineName", resolveFullEngineName(getDefaultEngineName()));
  }

  public static String resolveFullEngineName(final String engineName) {
    return System.getProperty("enginePrefix", "") + engineName;
  }

  public static String getZeebeDockerVersion() {
    return PROPERTIES.getProperty("zeebe.docker.version");
  }

  public static DatabaseType getDatabaseType() {
    return DatabaseType.fromString(PROPERTIES.getProperty("database.type"));
  }

  public static String getEngineDateFormat() {
    return PROPERTIES.getProperty("camunda.engine.serialization.date.format");
  }

  public static String getEngineItPluginEndpoint() {
    return getEngineSchemeAndAuthority()
        + PROPERTIES.getProperty("camunda.engine.it.plugin.endpoint");
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

  public static String getEmbeddedOptimizeEndpoint(final ApplicationContext applicationContext) {
    return "http://localhost:"
        + applicationContext
            .getBean(JettyConfig.class)
            .getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY)
        + applicationContext.getBean(JettyConfig.class).getContextPath().orElse("");
  }

  public static String getSecuredEmbeddedOptimizeEndpoint(
      final ApplicationContext applicationContext) {
    return "https://localhost:"
        + applicationContext
            .getBean(JettyConfig.class)
            .getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)
        + applicationContext.getBean(JettyConfig.class).getContextPath().orElse("");
  }

  public static String getEmbeddedOptimizeRestApiEndpoint(
      final ApplicationContext applicationContext) {
    return getEmbeddedOptimizeEndpoint(applicationContext) + REST_API_PATH;
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

  public static int getDatabaseMockServerPort() {
    return Integer.parseInt(System.getProperty("databaseMockServerPort", "1080"));
  }

  public static int getEngineMockServerPort() {
    return Integer.parseInt(System.getProperty("engineMockServerPort", "1090"));
  }
}
