/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public final class HubPolicyUpdateSchedulerPartitionTest {

  /** Records the polls of every partition; each poll stays open, like a Hub long poll. */
  private final List<Long> polls = new CopyOnWriteArrayList<>();

  private final HubPolicyClient hub =
      afterSeqNo -> {
        polls.add(afterSeqNo);
        return new CompletableFuture<List<PolicyUpdateRecord>>();
      };

  @Rule public final EngineRule engine = EngineRule.multiplePartition(3).withHubPolicyClient(hub);

  @Test
  public void shouldPollHubOnlyFromPartitionOne() {
    // given partition 1 has started its poll
    Awaitility.await()
        .untilAsserted(
            () -> {
              engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL);
              assertThat(polls).isNotEmpty();
            });

    // when
    IntStream.range(0, 5)
        .forEach(cycle -> engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL));

    // then the open poll of partition 1 is the only one; the other partitions never poll
    assertThat(polls).containsExactly(0L);
  }
}
