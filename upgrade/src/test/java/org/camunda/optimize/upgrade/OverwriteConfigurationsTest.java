/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class OverwriteConfigurationsTest {

  @Before
  public void setUp() throws Exception {
    deleteEnvConfig();
  }

  @After
  public void cleanUp() throws Exception {
    deleteEnvConfig();
  }

  @Test
  public void verifyConfigurationCanBeOverwritten() throws Exception {
    // given
    createEnvConfig(
      "es:\n" +
      "  connection:\n" +
        "    nodes:\n" +
        "    - host: 'foo'\n" +
        "      httpPort: 9200"
    );

    // when
    ConfigurationService configuration = ConfigurationServiceBuilder.createDefaultConfiguration();

    // then
    assertThat(configuration.getElasticsearchConnectionNodes().size(), is(1));
    assertThat(configuration.getElasticsearchConnectionNodes().get(0).getHost(), is("foo"));
  }

}
