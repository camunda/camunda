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
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterPurgeCleanupStrategy implements CleanupStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterPurgeCleanupStrategy.class);

  @Override
  public void cleanup(
      final CamundaManagementClient managementClient,
      final Supplier<CamundaClient> clientSupplier,
      final Instant testCaseStartTime) {
    LOG.debug("Purging cluster runtime data");
    final Instant startTime = Instant.now();

    managementClient.purgeCluster();

    final Duration duration = Duration.between(startTime, Instant.now());
    LOG.debug("Cluster runtime data purged in {}", duration);
  }
}
