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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class StreamRuntimeTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldProduceBeforeCommitAndPersistStateWithOffsetAtomically() throws Exception {
    // given — a runtime whose collaborators record the exact order of the commit barrier
    final List<String> order = new ArrayList<>();
    final Map<Integer, Long> committedOffsets = new HashMap<>();
    final CountDownLatch offsetCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    // one batch, then empty forever
    final Event event = new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(event)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              order.add("commitOffset");
              offsetCommitted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final List<String> processed = new ArrayList<>();
    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {
            processed.add(record);
          }

          @Override
          public void flush() {
            order.add("task.flush");
          }

          @Override
          public void checkpoint() {
            order.add("task.checkpoint");
          }
        };

    final OffsetStore offsets =
        new OffsetStore() {
          @Override
          public Map<Integer, Long> restore() {
            return Map.of();
          }

          @Override
          public void store(final int partition, final long offset) {
            order.add("offset.store");
            committedOffsets.put(partition, offset);
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
            .taskFactory(partition -> task)
            .transactionRunner(
                operations -> {
                  order.add("txn.begin");
                  operations.run();
                  order.add("txn.end");
                })
            .offsetStore(offsets)
            .preCommitFlush(() -> order.add("preCommitFlush"))
            .commitInterval(Duration.ZERO)
            .build();

    // when — run the loop until the first offset commit, then stop
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(offsetCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — the record was processed, and the barrier ran produce-before-commit with the offset
    // and
    // the checkpoint inside one transaction, before the source offset was committed.
    assertThat(processed).containsExactly("a");
    assertThat(committedOffsets).containsEntry(1, 5L);
    assertThat(order)
        .containsSubsequence(
            "task.flush",
            "preCommitFlush",
            "txn.begin",
            "offset.store",
            "task.checkpoint",
            "txn.end",
            "commitOffset");
  }

  @Test
  void shouldSeekToRestoredOffsetPlusOne() throws Exception {
    // given — a durable offset of 10 for partition 1
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(consumer.poll(anyInt(), any())).thenReturn(List.of());

    final CountDownLatch sought = new CountDownLatch(1);
    final Map<TopicPartition, Long> seekArg = new HashMap<>();
    doAnswerSeek(consumer, seekArg, sought);

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> mock(Task.class))
            .transactionRunner(Runnable::run)
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of(1, 10L);
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the consumer is sought to lastProcessed + 1
    assertThat(sought.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(seekArg).containsEntry(new TopicPartition(TOPIC, 1), 11L);
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @SuppressWarnings("unchecked")
  private static void doAnswerSeek(
      final Consumer consumer,
      final Map<TopicPartition, Long> captured,
      final CountDownLatch sought) {
    final AtomicBoolean once = new AtomicBoolean();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              if (once.compareAndSet(false, true)) {
                captured.putAll((Map<TopicPartition, Long>) invocation.getArgument(0));
                sought.countDown();
              }
              return null;
            })
        .when(consumer)
        .seek(any());
  }
}
