/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class UpgradeExecutionPlanTest {

  @Test
  public void testInitializeSchemaIsCalled() {
    final UpgradeExecutionPlan underTest = new UpgradeExecutionPlan();
    final SchemaUpgradeClient schemaUpgradeClient = Mockito.mock(SchemaUpgradeClient.class);
    underTest.setSchemaUpgradeClient(schemaUpgradeClient);
    underTest.setFromVersion("1");
    underTest.setToVersion("2");

    underTest.execute();

    verify(schemaUpgradeClient, times(1)).initializeSchema();
  }
}
