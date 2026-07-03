/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class StreamRuntimeGroupTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldBuildOneMemberPerIndexAndRunEachOnItsOwnLoop() throws Exception {
    // given — a group of 3 members, each built by the factory and each running its own consumer
    final Set<Integer> builtIndices = ConcurrentHashMap.newKeySet();
    final Set<String> subscribedInstances = ConcurrentHashMap.newKeySet();
    final CountDownLatch allSubscribed = new CountDownLatch(3);

    final StreamRuntimeGroup group =
        StreamRuntimeGroup.of(
            3,
            index -> {
              builtIndices.add(index);
              return member("member-" + index, subscribedInstances, allSubscribed);
            });

    // when — start launches every member on its own thread
    group.start();

    // then — the factory produced one member per index, and all three loops are independently live
    assertThat(builtIndices).containsExactlyInAnyOrder(0, 1, 2);
    assertThat(allSubscribed.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(subscribedInstances).containsExactlyInAnyOrder("member-0", "member-1", "member-2");

    // and — close stops every member and the threads drain without hanging
    group.close();
  }

  @Test
  void shouldRejectNonPositiveParallelism() {
    assertThatThrownBy(() -> StreamRuntimeGroup.of(0, index -> null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * A minimal idle runtime: it subscribes (recording that its loop started), then polls nothing.
   */
  private static StreamRuntime<String> member(
      final String instanceId,
      final Set<String> subscribedInstances,
      final CountDownLatch allSubscribed) {
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              subscribedInstances.add(invocation.getArgument(1));
              allSubscribed.countDown();
              return CompletableFuture.completedFuture(consumer);
            });
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of());

    return StreamRuntime.<String>builder()
        .client(client)
        .group("g")
        .instanceId(instanceId)
        .sourceTopic(TOPIC)
        .deserializer((payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
        .taskFactory(partition -> mock(Task.class))
        .transactionRunner(Runnable::run)
        .offsetStore(
            new OffsetStore() {
              @Override
              public Map<Integer, Long> restore() {
                return Map.of();
              }

              @Override
              public void store(final int partition, final long offset) {}
            })
        .build();
  }
}
