/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.cleanup;

import static org.mockito.Mockito.verify;

import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterPurgeCleanupStrategyTest {

  @Mock private CamundaManagementClient managementClient;

  @Test
  void shouldPurgeCluster() {
    // given
    final ClusterPurgeCleanupStrategy strategy = new ClusterPurgeCleanupStrategy();

    // when
    strategy.cleanup(managementClient, () -> null, Instant.now());

    // then
    verify(managementClient).purgeCluster();
  }
}
