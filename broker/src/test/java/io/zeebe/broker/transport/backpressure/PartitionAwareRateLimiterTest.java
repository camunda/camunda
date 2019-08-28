/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.limit.SettableLimit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public class PartitionAwareRateLimiterTest {
  private static final Supplier<Limit> LIMIT_SUPPLIER = () -> new SettableLimit(1);
  private static final int PARTITIONS = 3;

  private final PartitionAwareRequestLimiter partitionedLimiter =
      new PartitionAwareRequestLimiter(LIMIT_SUPPLIER);

  @Before
  public void setUp() {
    IntStream.range(0, PARTITIONS).forEach(i -> partitionedLimiter.addPartition(i));
  }

  @Test
  public void shouldPartitionsHaveItsOwnLimiter() {
    IntStream.range(0, PARTITIONS)
        .forEach(i -> assertThat(partitionedLimiter.tryAcquire(i, 0, 1, null)).isTrue());
  }

  @Test
  public void shouldUpdateOnResponse() {
    // given
    partitionedLimiter.tryAcquire(0, 0, 1, null);
    assertThat(partitionedLimiter.tryAcquire(0, 0, 2, null)).isFalse();

    // when
    partitionedLimiter.onResponse(0, 0, 1);

    // then
    assertThat(partitionedLimiter.tryAcquire(0, 0, 2, null)).isTrue();
  }

  @Test
  public void shouldNotUpdateOnResponseDifferentPartition() {
    final int mainPartitionId = 0;
    final int otherPartitionId = 1;
    // given
    partitionedLimiter.tryAcquire(mainPartitionId, 0, 1, null);
    partitionedLimiter.tryAcquire(otherPartitionId, 0, 1, null);
    assertThat(partitionedLimiter.tryAcquire(mainPartitionId, 0, 2, null)).isFalse();

    // when
    partitionedLimiter.onResponse(otherPartitionId, 0, 1);

    // then
    assertThat(partitionedLimiter.tryAcquire(mainPartitionId, 0, 2, null)).isFalse();
  }
}
