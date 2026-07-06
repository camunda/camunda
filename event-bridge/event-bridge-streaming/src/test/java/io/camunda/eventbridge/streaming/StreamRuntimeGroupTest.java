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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
              return idleMember("member-" + index, subscribedInstances, allSubscribed);
            });
    try {
      // when — start launches every member on its own thread
      group.start();

      // then — one member per index, and all three loops are independently live
      assertThat(builtIndices).containsExactlyInAnyOrder(0, 1, 2);
      assertThat(allSubscribed.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(subscribedInstances).containsExactlyInAnyOrder("member-0", "member-1", "member-2");
    } finally {
      group.close();
    }
  }

  @Test
  void shouldRejectNonPositiveParallelism() {
    assertThatThrownBy(() -> StreamRuntimeGroup.of(0, index -> null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRestartAMemberThatCrashes() throws Exception {
    // given — a member whose first generation fails while subscribing, and whose next comes up
    final AtomicInteger builds = new AtomicInteger();
    final CountDownLatch healthyRunning = new CountDownLatch(1);
    final StreamRuntimeGroup group =
        StreamRuntimeGroup.of(
            1,
            index -> failingThenHealthyMember(builds, healthyRunning),
            Duration.ofMillis(20),
            Duration.ofSeconds(5));
    try {
      // when
      group.start();

      // then — the crashed generation is replaced and the healthy one comes up
      assertThat(healthyRunning.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(builds.get()).isGreaterThanOrEqualTo(2);
    } finally {
      group.close();
    }
  }

  @Test
  void shouldNotRestartAMemberThatStopsCleanly() throws Exception {
    // given — a healthy member that only ever gets built once
    final AtomicInteger builds = new AtomicInteger();
    final CountDownLatch running = new CountDownLatch(1);
    final StreamRuntimeGroup group =
        StreamRuntimeGroup.of(
            1,
            index -> {
              builds.incrementAndGet();
              return idleMember("member-clean", ConcurrentHashMap.newKeySet(), running);
            },
            Duration.ofMillis(20),
            Duration.ofSeconds(5));
    group.start();
    assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

    // when — a graceful stop
    group.close();

    // then — no restart happened: the factory was called exactly once
    assertThat(builds.get()).isEqualTo(1);
  }

  @Test
  void shouldInterruptAMemberThatOverrunsTheShutdownTimeout() throws Exception {
    // given — a wedged member parked in poll, ignoring the cooperative stop
    final CountDownLatch polling = new CountDownLatch(1);
    final StreamRuntime<String> wedged = wedgedMember(polling);
    final StreamRuntimeGroup group =
        StreamRuntimeGroup.of(1, index -> wedged, Duration.ofSeconds(1), Duration.ofMillis(100));
    group.start();
    assertThat(polling.await(5, TimeUnit.SECONDS)).isTrue();

    // when — close must not hang: it waits the timeout, then interrupts to unwedge the member
    final long startNanos = System.nanoTime();
    group.close();
    final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

    // then — it returned well before the (5s) interrupt grace, i.e. the interrupt did the job
    assertThat(elapsedMs).isLessThan(4_000L);
  }

  /** A healthy member that subscribes (recording it) then idles, polling nothing. */
  private static StreamRuntime<String> idleMember(
      final String instanceId,
      final Set<String> subscribedInstances,
      final CountDownLatch subscribed) {
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              subscribedInstances.add(invocation.getArgument(1));
              subscribed.countDown();
              return CompletableFuture.completedFuture(consumer);
            });
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of());
    return baseRuntime(client, instanceId);
  }

  /** First build subscribes with a failed future (crashing the loop); later builds are healthy. */
  private static StreamRuntime<String> failingThenHealthyMember(
      final AtomicInteger builds, final CountDownLatch healthyRunning) {
    final boolean firstGeneration = builds.getAndIncrement() == 0;
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    if (firstGeneration) {
      when(client.subscribe(any(), any(), any()))
          .thenReturn(
              CompletableFuture.failedFuture(new IllegalStateException("boom on subscribe")));
    } else {
      when(client.subscribe(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                healthyRunning.countDown();
                return CompletableFuture.completedFuture(consumer);
              });
      when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
      when(consumer.poll(anyInt(), any())).thenReturn(List.of());
    }
    return baseRuntime(client, "member-restart");
  }

  /** A member whose poll parks until interrupted, ignoring the cooperative stop. */
  private static StreamRuntime<String> wedgedMember(final CountDownLatch polling) {
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              polling.countDown();
              try {
                Thread.sleep(60_000); // only an interrupt gets us out
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.of();
            });
    return baseRuntime(client, "member-wedged");
  }

  private static StreamRuntime<String> baseRuntime(
      final EventBridgeClient client, final String instanceId) {
    return StreamRuntime.<String>builder()
        // Short subscribe-retry backoff so a persistently-failing subscribe exhausts its attempt
        // budget and crashes the member quickly (the restart path under test) rather than retrying
        // for ~30s and overrunning the test's latch.
        .errorBackoff(Duration.ofMillis(1))
        .client(client)
        .group("g")
        .instanceId(instanceId)
        .sourceTopic(TOPIC)
        .deserializer((payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
        .taskFactory(partition -> mockTask())
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

  @SuppressWarnings("unchecked")
  private static Task<String> mockTask() {
    return mock(Task.class);
  }
}
