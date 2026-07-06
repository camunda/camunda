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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class StreamRuntimeParallelismTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldProcessDifferentPartitionsInParallel() throws Exception {
    // given — one poll batch with a record on each of two partitions, and a two-thread pool
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                new Event(1L, TOPIC, 1, "p1".getBytes(StandardCharsets.UTF_8)),
                new Event(1L, TOPIC, 2, "p2".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // A task whose process() only completes if the *other* partition is processing at the same
    // time:
    // both must be in process() concurrently for the shared latch to reach zero. Serial processing
    // would leave the first parked forever.
    final CountDownLatch bothInProcess = new CountDownLatch(2);
    final CountDownLatch bothDone = new CountDownLatch(2);
    final AtomicBoolean ranConcurrently = new AtomicBoolean(false);
    final Task<String> task =
        record -> {
          bothInProcess.countDown();
          try {
            if (bothInProcess.await(5, TimeUnit.SECONDS)) {
              ranConcurrently.set(true);
            }
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          bothDone.countDown();
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
            .transactionRunner(Runnable::run)
            .processorThreads(2)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — both partitions were in process() at the same time
    assertThat(bothDone.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(ranConcurrently).isTrue();

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldKeepFoldingWhileAPartitionsCommitBlocksOnASlowSink() throws Exception {
    // given — a single actor thread, and two partitions delivered one poll apart
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(List.of(new Event(1L, TOPIC, 1, "p1".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of(new Event(1L, TOPIC, 2, "p2".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final CountDownLatch p1CommitBlocked = new CountDownLatch(1);
    final CountDownLatch releaseP1Commit = new CountDownLatch(1);
    final CountDownLatch p2Processed = new CountDownLatch(1);

    // Partition 1 owns durability and blocks inside its commit; partition 2 just folds a record.
    final Task<String> p1 =
        new Task<>() {
          @Override
          public boolean ownsDurability() {
            return true;
          }

          @Override
          public long restore() {
            return 0L; // a real baseline (not NO_OFFSET), so it resumes rather than rebuilds
          }

          @Override
          public void process(final String record) {}

          @Override
          public void commit(final long offset) {
            p1CommitBlocked.countDown();
            try {
              releaseP1Commit.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        };
    final Task<String> p2 =
        new Task<>() {
          @Override
          public boolean ownsDurability() {
            return true;
          }

          @Override
          public long restore() {
            return 0L;
          }

          @Override
          public void process(final String record) {
            p2Processed.countDown();
          }

          @Override
          public void commit(final long offset) {}
        };

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> partition == 1 ? p1 : p2)
            .processorThreads(1) // ONE actor thread: proves the blocking commit is off it
            .sinkIoThreads(2)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — partition 1 is stuck in its commit (on the sink executor), yet the single actor thread
    // is free to fold partition 2. A synchronous commit would have wedged the only actor thread.
    assertThat(p1CommitBlocked.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(p2Processed.await(5, TimeUnit.SECONDS)).isTrue();

    releaseP1Commit.countDown();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }
}
