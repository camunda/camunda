/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Parser;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.client.ConsumerNotRegisteredException;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verifies the async commit path of {@link GroupCoordinator}: a 404/409 (CONSUMER_NOT_REGISTERED)
 * triggers a single rejoin-then-retry, and a repeated rejection propagates.
 */
final class GroupCoordinatorCommitTest {

  private HttpTransport transport;
  private ScheduledExecutorService executor;
  private SubscriptionState subscription;
  private PrefetchBuffer buffer;
  private Prefetcher prefetcher;
  private GroupCoordinator coordinator;

  @BeforeEach
  void setUp() {
    transport = Mockito.mock(HttpTransport.class);
    executor = Executors.newSingleThreadScheduledExecutor();
    subscription =
        new SubscriptionState(
            (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
                CompletableFuture.completedFuture(null),
            OffsetResetPolicy.EARLIEST);
    buffer = new PrefetchBuffer(1);
    prefetcher =
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
    // parse delegates to the real protobuf parser for the rejoin JoinResponse.
    when(transport.parse(any(), any(), any()))
        .thenAnswer(
            inv -> {
              final Parser<?> parser = inv.getArgument(1);
              return parser.parseFrom((byte[]) inv.getArgument(0));
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
            3_000L);

    // Establish an initial memberId via a one-off join so commit request paths can URL-encode it,
    // then clear invocations so each test asserts only its own commit/rejoin traffic.
    when(transport.postProtobufRaw(contains("/join"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, joinResponse("c1", 1))));
    coordinator.rejoin().join();
    Mockito.clearInvocations(transport);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldRetryCommitOnceAfterRejoinWhenNotRegistered() {
    // given: first commit is rejected with 409, rejoin succeeds, retried commit succeeds (204)
    when(transport.postProtobufRaw(contains("/commit"), any(), eq("commitOffset")))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(409, new byte[0])))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(204, new byte[0])));
    when(transport.postProtobufRaw(contains("/join"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, joinResponse("c1-2", 5))));

    // when
    coordinator.commitOffset("t1", 1, 42L).join();

    // then: exactly one rejoin and exactly two commit attempts
    verify(transport, times(1)).postProtobufRaw(contains("/join"), any(), eq("rejoin"));
    verify(transport, times(2)).postProtobufRaw(contains("/commit"), any(), eq("commitOffset"));
    assertThat(coordinator.memberId()).isEqualTo("c1-2");
    assertThat(coordinator.memberEpoch()).isEqualTo(5L);
  }

  @Test
  void shouldPropagateWhenRetriedCommitAlsoNotRegistered() {
    // given: both commit attempts are rejected, rejoin succeeds
    when(transport.postProtobufRaw(contains("/commit"), any(), eq("commitOffset")))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(404, new byte[0])))
        .thenReturn(CompletableFuture.completedFuture(new BinaryResponse(404, new byte[0])));
    when(transport.postProtobufRaw(contains("/join"), any(), eq("rejoin")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, joinResponse("c1-2", 5))));

    // when / then: the second rejection propagates and there is no second rejoin
    assertThatThrownBy(() -> coordinator.commitOffset("t1", 1, 42L).join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(ConsumerNotRegisteredException.class);
    verify(transport, times(1)).postProtobufRaw(contains("/join"), any(), eq("rejoin"));
    verify(transport, times(2)).postProtobufRaw(contains("/commit"), any(), eq("commitOffset"));
  }

  private static byte[] joinResponse(final String memberId, final long epoch) {
    return JoinResponse.newBuilder()
        .setMemberId(memberId)
        .setMemberEpoch(epoch)
        .build()
        .toByteArray();
  }
}
