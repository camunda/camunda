/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The topology instantiates an isolated, partition-local processor per partition. */
final class StreamTopologyTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldBuildAnIsolatedStagePerPartition() {
    // given — a stage factory whose state (a counter) is created per partition
    final Map<Integer, long[]> counters = new HashMap<>();
    final StreamTopology<String> topology =
        new StreamTopology<String>(partition -> inMemoryShard())
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
  void shouldWireIntoTheRuntimeAsTheTaskFactory() throws Exception {
    // given — a counting topology used directly as the runtime's task factory
    final Map<Integer, long[]> counters = new HashMap<>();
    final CountDownLatch processed = new CountDownLatch(3);
    final StreamTopology<String> topology =
        new StreamTopology<String>(partition -> inMemoryShard())
            .add(
                partitionId -> {
                  final long[] count = counters.computeIfAbsent(partitionId, p -> new long[1]);
                  return record -> {
                    count[0]++;
                    processed.countDown();
                  };
                });

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                new Event(1L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8)),
                new Event(2L, TOPIC, 1, "b".getBytes(StandardCharsets.UTF_8)),
                new Event(1L, TOPIC, 2, "c".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(topology::processorFor) // the wiring under test
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(processed.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — each partition ran its own processor with partition-local state
    assertThat(counters.get(1)[0]).isEqualTo(2L);
    assertThat(counters.get(2)[0]).isEqualTo(1L);
  }

  /** A heap shard with a restored baseline of 0, so materialization resumes, not rebuilds. */
  private static ShardDurability inMemoryShard() {
    return new ShardDurability() {
      private long offset;

      @Override
      public void runInTransaction(final Runnable operations) {
        operations.run();
      }

      @Override
      public long readOffset() {
        return offset;
      }

      @Override
      public void persistOffset(final long committed) {
        offset = committed;
      }
    };
  }
}
