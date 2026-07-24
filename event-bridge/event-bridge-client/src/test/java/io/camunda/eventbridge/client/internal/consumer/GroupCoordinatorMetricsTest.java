/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.protobuf.Parser;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.client.ConsumerMetrics;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verifies {@link ConsumerMetrics} is wired at every seam {@code GroupCoordinator} touches: the
 * no-op default when nothing is configured, and each callback firing exactly where the
 * static-membership takeover monitoring spec (phase 2 of task #24) calls for it.
 */
final class GroupCoordinatorMetricsTest {

  /** Long enough that a scheduled beat never fires during a test. */
  private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

  private HttpTransport transport;
  private ScheduledThreadPoolExecutor executor;
  private GroupCoordinator coordinator;
  private RecordingMetrics metrics;

  @BeforeEach
  void setUp() {
    transport = Mockito.mock(HttpTransport.class);
    executor = new ScheduledThreadPoolExecutor(1);
    final SubscriptionState subscription =
        new SubscriptionState(
            (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
                CompletableFuture.completedFuture(null),
            OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final Prefetcher prefetcher =
        new Prefetcher(
            (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
                CompletableFuture.completedFuture(null),
            executor,
            subscription,
            buffer,
            () -> false,
            1 << 20,
            0,
            5_000L);
    when(transport.parse(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Parser<?> parser = invocation.getArgument(1);
              return parser.parseFrom((byte[]) invocation.getArgument(0));
            });
    coordinator =
        new GroupCoordinator(
            transport,
            executor,
            subscription,
            buffer,
            prefetcher,
            () -> false,
            "g1",
            List.of("t1"),
            "c1",
            () -> {},
            HEARTBEAT_INTERVAL_MS);
    metrics = new RecordingMetrics();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldWorkWithTheNoOpDefaultWhenNoMetricsAreConfigured() {
    // given — no metrics(...) call at all (the field defaults to ConsumerMetrics.noop())
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c1", 1))));
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, heartbeatResponse(1))));

    // when/then — every call site tolerates the no-op default without throwing
    coordinator.rejoin().join();
    coordinator.sendHeartbeat().join();
  }

  @Test
  void shouldReportEpochChangedOnJoinAndOnFullReconciliation() {
    coordinator.setMetrics(metrics);
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c1", 1))));
    coordinator.rejoin().join();
    assertThat(metrics.epochChanges).containsExactly(1L);

    // when — a heartbeat's full reconciliation carries a higher epoch (e.g. after a takeover)
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, heartbeatResponse(2))));
    coordinator.sendHeartbeat().join();

    assertThat(metrics.epochChanges).containsExactly(1L, 2L);
  }

  @Test
  void shouldReportRebalanceOnlyOnANonEmptyOwnedPartitionsDelta() {
    coordinator.setMetrics(metrics);

    // when — applyOwnedPartitions actually changes the owned set
    coordinator.applyOwnedPartitions(List.of(new TopicPartition("t1", 1)));
    assertThat(metrics.rebalances.get()).isEqualTo(1);

    // and — a static restart resolving to the SAME set (invariant 4: verbatim inheritance) must
    // NOT count as a rebalance
    coordinator.applyOwnedPartitions(List.of(new TopicPartition("t1", 1)));
    assertThat(metrics.rebalances.get()).isEqualTo(1);
  }

  @Test
  void shouldReportHeartbeatFailureOnATransportError() {
    establishMembership();
    coordinator.setMetrics(metrics);
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

    assertThatThrownByHeartbeat();

    assertThat(metrics.heartbeatFailures.get()).isEqualTo(1);
  }

  @Test
  void shouldReportFencedRejoinOnAGenuine409AndRejoinRejectedOnTheRejoinsOwn409() {
    establishMembership();
    coordinator.setMetrics(metrics);
    // given — a heartbeat that genuinely fences the current membership
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(409, new byte[0])));
    // and the triggered rejoin itself is rejected (e.g. a concurrent second join)
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(409, new byte[0])));

    coordinator.sendHeartbeat().join();

    assertThat(metrics.fencedRejoins.get()).isEqualTo(1);
    assertThat(metrics.rejoinsRejected.get()).isEqualTo(1);
  }

  /**
   * Establishes an initial membership ("c1", epoch 1) via a one-off rejoin, metrics not yet set.
   */
  private void establishMembership() {
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c1", 1))));
    coordinator.rejoin().join();
  }

  private void assertThatThrownByHeartbeat() {
    try {
      coordinator.sendHeartbeat().join();
    } catch (final RuntimeException expected) {
      // the transport failure propagates to the caller; only the metrics side effect matters here
    }
  }

  private static byte[] joinResponse(final String memberId, final long epoch) {
    return JoinResponse.newBuilder()
        .setMemberId(memberId)
        .setMemberEpoch(epoch)
        .build()
        .toByteArray();
  }

  private static byte[] heartbeatResponse(final long epoch) {
    return ConsumerHeartbeatResponse.newBuilder().setMemberEpoch(epoch).build().toByteArray();
  }

  /** A simple recording {@link ConsumerMetrics} — no Micrometer dependency in this module. */
  private static final class RecordingMetrics implements ConsumerMetrics {
    private final AtomicInteger rebalances = new AtomicInteger();
    private final AtomicInteger heartbeatFailures = new AtomicInteger();
    private final AtomicInteger fencedRejoins = new AtomicInteger();
    private final AtomicInteger rejoinsRejected = new AtomicInteger();
    private final List<Long> epochChanges = new CopyOnWriteArrayList<>();

    @Override
    public void onRebalance(final int revoked, final int assigned) {
      rebalances.incrementAndGet();
    }

    @Override
    public void onHeartbeatFailure() {
      heartbeatFailures.incrementAndGet();
    }

    @Override
    public void onFencedRejoin() {
      fencedRejoins.incrementAndGet();
    }

    @Override
    public void onRejoinRejected() {
      rejoinsRejected.incrementAndGet();
    }

    @Override
    public void onEpochChanged(final long memberEpoch) {
      epochChanges.add(memberEpoch);
    }
  }
}
