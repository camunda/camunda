/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.backpressure.BackpressureCfg;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public final class PartitionAwareRateLimiterTest {
  private static final int PARTITIONS = 3;
  private final Intent context = ProcessInstanceCreationIntent.CREATE;
  private PartitionAwareRequestLimiter partitionedLimiter;

  @Before
  public void setUp() {
    final var backpressureCfg = new BackpressureCfg();
    backpressureCfg.setAlgorithm("fixed");
    backpressureCfg.getFixed().setLimit(1);
    partitionedLimiter =
        io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter.newLimiter(
            backpressureCfg);
    IntStream.range(0, PARTITIONS).forEach(partitionedLimiter::addPartition);
  }

  @Test
  public void shouldNotBlockRequestsOnOtherPartitionsWhenOnePartitionIsFull() {
    // when
    assertThat(partitionedLimiter.tryAcquire(0, 0, 1, context)).isTrue();
    assertThat(partitionedLimiter.tryAcquire(0, 0, 2, context)).isFalse();

    // then
    IntStream.range(1, PARTITIONS)
        .forEach(i -> assertThat(partitionedLimiter.tryAcquire(i, 0, 1, context)).isTrue());
  }

  @Test
  public void shouldUpdateOnResponse() {
    // given
    partitionedLimiter.tryAcquire(0, 0, 1, context);
    assertThat(partitionedLimiter.tryAcquire(0, 0, 2, context)).isFalse();

    // when
    partitionedLimiter.onResponse(0, 0, 1);

    // then
    assertThat(partitionedLimiter.tryAcquire(0, 0, 2, context)).isTrue();
  }

  @Test
  public void shouldNotUpdateOnResponseDifferentPartition() {
    final int mainPartitionId = 0;
    final int otherPartitionId = 1;
    // given
    partitionedLimiter.tryAcquire(mainPartitionId, 0, 1, context);
    partitionedLimiter.tryAcquire(otherPartitionId, 0, 1, context);
    assertThat(partitionedLimiter.tryAcquire(mainPartitionId, 0, 2, context)).isFalse();

    // when
    partitionedLimiter.onResponse(otherPartitionId, 0, 1);

    // then
    assertThat(partitionedLimiter.tryAcquire(mainPartitionId, 0, 2, context)).isFalse();
  }
}
