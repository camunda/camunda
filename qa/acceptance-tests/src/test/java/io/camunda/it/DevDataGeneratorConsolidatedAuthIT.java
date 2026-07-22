/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class DevDataGeneratorConsolidatedAuthIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAdditionalProfile("dev-data")
          .withAdditionalProfile("consolidated-auth")
          .withUnauthenticatedAccess();

  private static CamundaClient camundaClient;

  @Test
  void shouldSeedDemoProcessDefinitionsWithoutAuthenticationFailure() {
    waitForProcessesToBeDeployed(camundaClient, 1);
  }
}
