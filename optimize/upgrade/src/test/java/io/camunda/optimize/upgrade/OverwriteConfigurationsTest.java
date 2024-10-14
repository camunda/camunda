/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.createEnvConfig;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OverwriteConfigurationsTest {

  @BeforeEach
  public void setUp() throws Exception {
    deleteEnvConfig();
  }

  @AfterEach
  public void cleanUp() throws Exception {
    deleteEnvConfig();
  }

  @Test
  public void verifyConfigurationCanBeOverwritten() throws Exception {
    // given
    createEnvConfig(
        "es:\n"
            + "  connection:\n"
            + "    nodes:\n"
            + "    - host: 'foo'\n"
            + "      httpPort: 9200");

    // when
    ConfigurationService configuration = ConfigurationServiceBuilder.createDefaultConfiguration();

    // then
    assertThat(configuration.getElasticSearchConfiguration().getConnectionNodes()).hasSize(1);
    assertThat(configuration.getElasticSearchConfiguration().getConnectionNodes().get(0).getHost())
        .isEqualTo("foo");
  }
}
