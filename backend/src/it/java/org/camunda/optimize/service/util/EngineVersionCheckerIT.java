/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_CONTEXT_LOCATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class EngineVersionCheckerIT extends AbstractIT {

  @RegisterExtension
  @Order(5)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension2 =
    new EmbeddedOptimizeExtension("org.camunda.optimize.service.util.VersionCheckerSpringITConfig");

  @Test
  public void engineVersionCantBeDetermined() {
    embeddedOptimizeExtension2.stopOptimize();

    try {
      embeddedOptimizeExtension2.startOptimize();
    } catch (Exception e) {
      //expected
      assertThat(
        e.getCause()
          .getMessage()
          .contains("While checking the Engine version, following error occurred: Status code: 404,\n this " +
                      "means you either configured a wrong endpoint or you have an unsupported engine version < "),
        is(true)
      );
      return;
    }

    fail("Exception expected");
  }

  @AfterEach
  public void setContextBack() throws Exception {
    embeddedOptimizeExtension2.stopOptimize();
    EmbeddedOptimizeExtension embeddedOptimizeExtension =
      new EmbeddedOptimizeExtension(DEFAULT_CONTEXT_LOCATION);
    embeddedOptimizeExtension.startOptimize();
  }
}
