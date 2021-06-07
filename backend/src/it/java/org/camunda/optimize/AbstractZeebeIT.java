/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractZeebeIT extends AbstractIT {

  @RegisterExtension
  @Order(5)
  protected ZeebeExtension zeebeExtension = new ZeebeExtension();

  @BeforeEach
  public void setup() {
    // First we clear all existing Zeebe records in Optimize
    final ZeebeConfiguration configuredZeebe = embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe();
    final String existingPrefix = configuredZeebe.getName();
    elasticSearchIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(existingPrefix);
    // Then we set the new record prefix for the next test
    configuredZeebe.setName(zeebeExtension.getZeebeRecordPrefix());
  }

  protected void importAllZeebeEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
