/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class EngineVersionCheckerIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule2 =
    new EmbeddedOptimizeExtensionRule("classpath:versionCheckContext.xml");

  @Test
  public void engineVersionCantBeDetermined() {
    embeddedOptimizeExtensionRule2.stopOptimize();

    try {
      embeddedOptimizeExtensionRule2.startOptimize();
    } catch (Exception e) {
      //expected
      assertThat(e.getCause().getMessage().contains("Engine version is not supported"), is(true));
      return;
    }

    fail("Exception expected");
  }

  @AfterEach
  public void setContextBack() throws Exception {
    embeddedOptimizeExtensionRule2.stopOptimize();
    EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule("classpath:embeddedOptimizeContext.xml");
    embeddedOptimizeExtensionRule.startOptimize();
  }
}
