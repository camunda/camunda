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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class StreamRuntimeRebalanceTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldRebuildAnAssignedOwningShardAndReleaseARevokedOne() throws Exception {
    final AtomicReference<RebalanceListener> listener = new AtomicReference<>();
    final List<Collection<TopicPartition>> sought = new CopyOnWriteArrayList<>();
    final java.util.Set<Integer> built = ConcurrentHashMap.newKeySet();
    final CountDownLatch registered = new CountDownLatch(1);
    final CountDownLatch soughtToStart = new CountDownLatch(1);
    final CountDownLatch closed = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of());
    doAnswer(
            invocation -> {
              listener.set(invocation.getArgument(0));
              registered.countDown();
              return null;
            })
        .when(consumer)
        .rebalanceListener(any());
    doAnswer(
            invocation -> {
              sought.add(invocation.getArgument(0));
              soughtToStart.countDown();
              return null;
            })
        .when(consumer)
        .seekToBeginning(any());

    // A self-owning shard with no local state (restore == NO_OFFSET), latching its close().
    final Task<String> owningTask =
        new Task<>() {
          @Override
          public boolean ownsDurability() {
            return true;
          }

          @Override
          public long restore() {
            return Task.NO_OFFSET;
          }

          @Override
          public void process(final String record) {}

          @Override
          public void close() {
            closed.countDown();
          }
        };

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(
                partition -> {
                  built.add(partition);
                  return owningTask;
                })
            .build();

    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();

    // when — partition 1 is assigned
    listener.get().onPartitionsAssigned(List.of(new TopicPartition(TOPIC, 1)));

    // then — the run loop materializes it and, having no local state, rebuilds from the source
    // start
    assertThat(soughtToStart.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(built).contains(1);
    assertThat(sought.get(0)).containsExactly(new TopicPartition(TOPIC, 1));

    // when — partition 1 is revoked
    listener.get().onPartitionsRevoked(List.of(new TopicPartition(TOPIC, 1)));

    // then — its task is released (closed)
    assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }
}
