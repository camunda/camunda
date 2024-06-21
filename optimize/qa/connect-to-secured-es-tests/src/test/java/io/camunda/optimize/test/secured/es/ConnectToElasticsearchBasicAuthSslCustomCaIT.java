/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.secured.es;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createConfigurationWithDefaultAndAdditionalLocations;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

public class ConnectToElasticsearchBasicAuthSslCustomCaIT extends AbstractConnectToElasticsearchIT {

  private static final String CONFIG_FILE = "secured-connection-basic-auth-ssl-custom-ca.yaml";

  @Override
  protected String getCustomConfigFile() {
    return CONFIG_FILE;
  }

  @TestConfiguration
  static class Configuration {

    @Bean
    @Primary
    public ConfigurationService configurationService() {
      return createConfigurationWithDefaultAndAdditionalLocations(CONFIG_FILE);
    }
  }
}
