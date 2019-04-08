/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;

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
  public void verifyDateFormatEnhancedFromConfig() throws Exception {
    // given
    createEnvConfig(
      "es:\n" +
      "  connection:\n" +
        "    nodes:\n" +
        "    - host: 'foo'\n" +
        "      httpPort: 9200"
    );

    UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("2.0.0")
      .toVersion("2.1.0")
      .build();

    try {
      // when
      upgradePlan.execute();
      fail("Should throw an error, since the Elasticsearch host 'foo' does not exist!");
    } catch (Exception e) {
      // then this should throw an error
    }

  }

}
