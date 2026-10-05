/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineAction;
import io.camunda.zeebe.rebalance.LoadMeasure;
import io.camunda.zeebe.stream.impl.metrics.StreamProcessorAction;
import org.junit.jupiter.api.Test;

final class BrokerLoadCountersTest {

  private final BrokerLoadCounters counters = new BrokerLoadCounters();

  @Test
  void shouldSumEveryRegisteredPartition() {
    // given
    final var first = counters.register();
    final var second = counters.register();

    // when
    first.processing().add(StreamProcessorAction.PROCESSED, 3);
    second.processing().add(StreamProcessorAction.PROCESSED, 4);
    second.rootProcessInstances().increment(EngineAction.ACTIVATED);

    // then
    assertThat(counters.total(LoadMeasure.PROCESSED_COMMANDS)).isEqualTo(7);
    assertThat(counters.total(LoadMeasure.ROOT_PROCESS_INSTANCES)).isEqualTo(1);
  }

  @Test
  void shouldKeepWhatARetiredPartitionCounted() {
    // given
    final var partition = counters.register();
    partition.processing().add(StreamProcessorAction.PROCESSED, 5);
    partition.rootProcessInstances().add(EngineAction.ACTIVATED, 2);

    // when
    counters.retire(partition);

    // then
    assertThat(counters.total(LoadMeasure.PROCESSED_COMMANDS)).isEqualTo(5);
    assertThat(counters.total(LoadMeasure.ROOT_PROCESS_INSTANCES)).isEqualTo(2);
  }

  @Test
  void shouldStopTrackingARetiredPartition() {
    // given
    final var partition = counters.register();
    partition.processing().add(StreamProcessorAction.PROCESSED, 5);
    counters.retire(partition);

    // when
    partition.processing().add(StreamProcessorAction.PROCESSED, 100);
    counters.retire(partition);

    // then
    assertThat(counters.total(LoadMeasure.PROCESSED_COMMANDS)).isEqualTo(5);
  }
}
