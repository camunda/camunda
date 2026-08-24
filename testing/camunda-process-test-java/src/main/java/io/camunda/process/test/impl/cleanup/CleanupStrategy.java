/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.cleanup;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.util.function.Supplier;

/** Internal strategy contract for test cleanup behavior based on the configured deletion mode. */
public interface CleanupStrategy {

  /**
   * Executes test cleanup for data created since the provided test case start time.
   *
   * @param managementClient management API client
   * @param clientSupplier supplier to create a Camunda API client
   * @param testCaseStartTime start time of the current test case
   */
  void cleanup(
      CamundaManagementClient managementClient,
      Supplier<CamundaClient> clientSupplier,
      Instant testCaseStartTime);
}
