/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.InMemoryRollupStore;
import io.camunda.eventbridge.streaming.aggregate.PreAggregatingRollup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The topology instantiates an isolated, partition-local processor per partition. */
final class StreamTopologyTest {

  private record Sale(String region, long amount) {}

  private static final AggregateFunction<Sale, Long, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Long createAccumulator() {
          return 0L;
        }

        @Override
        public Long add(final Sale sale, final Long acc) {
          return acc + sale.amount();
        }

        @Override
        public Long merge(final Long a, final Long b) {
          return a + b;
        }

        @Override
        public Long getResult(final Long acc) {
          return acc;
        }
      };

  @Test
  void shouldBuildAnIsolatedStagePerPartition() {
    // given — a stage factory whose state (a counter) is created per partition
    final Map<Integer, long[]> counters = new HashMap<>();
    final StreamTopology<String> topology =
        new StreamTopology<String>()
            .add(
                partitionId -> {
                  final long[] count = counters.computeIfAbsent(partitionId, p -> new long[1]);
                  return record -> count[0]++;
                });

    // when — two partitions get their own processors
    final StreamProcessor<String> p0 = topology.processorFor(0);
    final StreamProcessor<String> p1 = topology.processorFor(1);
    p0.process("a");
    p0.process("b");
    p1.process("c");

    // then — state is partition-local, not shared
    assertThat(counters.get(0)[0]).isEqualTo(2L);
    assertThat(counters.get(1)[0]).isEqualTo(1L);
  }

  @Test
  void shouldBuildProjectionStagesWithPartitionLocalState() {
    // given — a per-partition serving store, captured so the test can read each back
    final Map<Integer, InMemoryRollupStore<String, Long>> stores = new HashMap<>();
    final StreamTopology<Sale> topology =
        new StreamTopology<Sale>()
            .add(
                partitionId -> {
                  final InMemoryRollupStore<String, Long> store = new InMemoryRollupStore<>(SUM);
                  stores.put(partitionId, store);
                  return new ProjectionStage<Sale, Sale>(
                      (sale, out) -> out.collect(sale),
                      List.of(new PreAggregatingRollup<>(SUM, Sale::region, store, 1_000)));
                });

    // when — each partition aggregates only its own facts
    final StreamProcessor<Sale> p0 = topology.processorFor(0);
    final StreamProcessor<Sale> p1 = topology.processorFor(1);
    p0.process(new Sale("EU", 100));
    p1.process(new Sale("EU", 40));
    p0.close();
    p1.close();

    // then — independent partial accumulators (a later cross-partition merge sums to 140)
    assertThat(stores.get(0).get("EU")).contains(100L);
    assertThat(stores.get(1).get("EU")).contains(40L);
  }
}
