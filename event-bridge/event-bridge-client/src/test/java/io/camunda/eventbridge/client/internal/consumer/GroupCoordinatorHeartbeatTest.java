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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.protobuf.Parser;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

/**
 * Verifies the heartbeat loop of {@link GroupCoordinator} stays a single chain: rescheduling
 * cancels the previously scheduled beat, so a second trigger (the initial join plus a manual {@code
 * sendHeartbeat()}) does not fork a parallel heartbeat chain that beats at a multiple of the
 * configured interval forever.
 */
final class GroupCoordinatorHeartbeatTest {

  /** Long enough that a scheduled beat never fires during the test. */
  private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

  private final List<ScheduledFuture<?>> scheduledBeats = new CopyOnWriteArrayList<>();

  private HttpTransport transport;
  private ScheduledThreadPoolExecutor executor;
  private GroupCoordinator coordinator;

  @BeforeEach
  void setUp() {
    transport = Mockito.mock(HttpTransport.class);
    // Spy the executor so every scheduled heartbeat future is captured for cancellation asserts.
    // Only delayed schedules are heartbeats — execute() also lands here, with a zero delay. The
    // coordinator's heartbeat lambda returns a future, so it binds to the Callable overload.
    executor = Mockito.spy(new ScheduledThreadPoolExecutor(1));
    Mockito.doAnswer(this::captureDelayedSchedule)
        .when(executor)
        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    Mockito.doAnswer(this::captureDelayedSchedule)
        .when(executor)
        .schedule(Mockito.<Callable<?>>any(), anyLong(), any(TimeUnit.class));

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

    // Establish a memberId via a one-off rejoin (rejoin does not schedule a heartbeat itself).
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c1", 1))));
    coordinator.rejoin().join();

    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, heartbeatResponse(1))));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  /** Records the returned future of a delayed {@code schedule(...)} call (a heartbeat beat). */
  private Object captureDelayedSchedule(final InvocationOnMock invocation) throws Throwable {
    final ScheduledFuture<?> future = (ScheduledFuture<?>) invocation.callRealMethod();
    if (invocation.getArgument(1, Long.class) > 0) {
      scheduledBeats.add(future);
    }
    return future;
  }

  @Test
  void shouldCancelPreviouslyScheduledBeatOnReschedule() {
    // given: a successful heartbeat scheduled the next beat of its chain
    coordinator.sendHeartbeat().join();
    assertThat(scheduledBeats).hasSize(1);
    final ScheduledFuture<?> firstChainBeat = scheduledBeats.get(0);
    assertThat(firstChainBeat.isCancelled()).isFalse();

    // when: a second trigger (e.g. a manual sendHeartbeat after the join already scheduled one)
    // completes and reschedules
    coordinator.sendHeartbeat().join();

    // then: the prior chain's beat is cancelled — exactly one live heartbeat chain remains
    assertThat(scheduledBeats).hasSize(2);
    assertThat(firstChainBeat.isCancelled()).isTrue();
    assertThat(scheduledBeats.get(1).isCancelled()).isFalse();
  }

  @Test
  void shouldKeepExactlyOneLiveBeatAcrossRepeatedTriggers() {
    // when: several manual heartbeats each complete and reschedule
    coordinator.sendHeartbeat().join();
    coordinator.sendHeartbeat().join();
    coordinator.sendHeartbeat().join();

    // then: every beat but the latest is cancelled — no parallel chains accumulate
    assertThat(scheduledBeats).hasSize(3);
    assertThat(scheduledBeats.subList(0, 2)).allMatch(ScheduledFuture::isCancelled);
    assertThat(scheduledBeats.get(2).isCancelled()).isFalse();
  }

  @Test
  void shouldDiscardAStale409FromAPreviousMembership() {
    // given a heartbeat in flight for the current member whose response is delayed
    final CompletableFuture<BinaryResponse> delayed = new CompletableFuture<>();
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(delayed);
    final CompletableFuture<Void> staleBeat = coordinator.sendHeartbeat();

    // and a rejoin that replaces the membership while that heartbeat is still in flight
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c2", 2))));
    coordinator.rejoin().join();

    // when the 409 for the PREVIOUS membership finally lands
    delayed.complete(new BinaryResponse(409, new byte[0]));
    staleBeat.join();

    // then it is discarded — the fresh membership is kept and no further rejoin is triggered
    // (one rejoin from setUp + one explicit above; a third would be the storm)
    assertThat(coordinator.memberId()).isEqualTo("c2");
    assertThat(coordinator.memberEpoch()).isEqualTo(2);
    Mockito.verify(transport, Mockito.times(2))
        .postProtobufRaw(contains("/members"), any(), eq("rejoin"));
  }

  @Test
  void shouldStillRejoinOnA409ForTheCurrentMembership() {
    // given a heartbeat whose 409 genuinely fences the CURRENT membership
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(409, new byte[0])));
    when(transport.postProtobufRaw(contains("/members"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("c2", 2))));

    // when it lands with no rejoin having replaced the identity meanwhile
    coordinator.sendHeartbeat().join();

    // then the coordinator re-registers (setUp's rejoin + this one)
    assertThat(coordinator.memberId()).isEqualTo("c2");
    Mockito.verify(transport, Mockito.times(2))
        .postProtobufRaw(contains("/members"), any(), eq("rejoin"));
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
}
