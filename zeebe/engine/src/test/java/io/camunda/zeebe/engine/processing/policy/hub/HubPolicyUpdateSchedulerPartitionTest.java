/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public final class HubPolicyUpdateSchedulerPartitionTest {

  private final StubFanoutClient.Factory clients = new StubFanoutClient.Factory();

  @Rule
  public final EngineRule engine = EngineRule.multiplePartition(3).withFanoutClientFactory(clients);

  @Test
  public void shouldStartAClientOnlyOnPartitionOne() {
    // given partition 1 has started its client
    Awaitility.await()
        .untilAsserted(
            () -> {
              engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL);
              assertThat(clients.clients).isNotEmpty();
            });

    // when
    IntStream.range(0, 5)
        .forEach(cycle -> engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL));

    // then the other partitions never start one
    assertThat(clients.clients).hasSize(1);
  }
}
