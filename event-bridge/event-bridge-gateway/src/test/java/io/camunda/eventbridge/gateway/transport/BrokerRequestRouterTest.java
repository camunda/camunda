/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.topology.TopologyService;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.transport.MessageTypes;
import io.camunda.eventbridge.gateway.transport.SbeCodec.PollParams;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BrokerRequestRouter}.
 *
 * <p>The test double for {@link MessagingService} is injected via a fake {@link AtomixCluster} so
 * that the router wires up the same way it does in production. All async futures are synchronously
 * resolved for deterministic assertions.
 */
class BrokerRequestRouterTest {

  private static final Address LEADER_ADDR = Address.from("localhost", 26501);
  private static final Address COORDINATOR_ADDR = Address.from("localhost", 26502);
  private static final int PARTITION_0 = 0;

  private MessagingService messagingService;
  private TopologyService topologyService;
  private BrokerRequestRouter router;

  @BeforeEach
  void setUp() {
    messagingService = mock(MessagingService.class);
    topologyService = mock(TopologyService.class);

    // Wire the mock MessagingService through a fake AtomixCluster.
    final AtomixCluster cluster = mock(AtomixCluster.class);
    when(cluster.getMessagingService()).thenReturn(messagingService);

    router = new BrokerRequestRouter(cluster, topologyService);

    when(topologyService.getLeaderAddress(PARTITION_0)).thenReturn(Optional.of(LEADER_ADDR));
    when(topologyService.getCoordinatorAddress()).thenReturn(Optional.of(COORDINATOR_ADDR));
  }

  // -------------------------------------------------------------------------
  // PublishBatch

  @Nested
  class PublishBatch {

    @Test
    void shouldReturnPositionsOnSuccess() throws Exception {
      // given
      final byte[] successResponse =
          buildPublishResponse(ErrorCode.NONE, List.of(100L, 101L, 102L));
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(successResponse));

      // when
      final List<Long> positions =
          router
              .publishBatch(PARTITION_0, List.of(new byte[] {1}, new byte[] {2}, new byte[] {3}))
              .get();

      // then
      assertThat(positions).containsExactly(100L, 101L, 102L);
    }

    @Test
    void shouldRetryOnTransportFailureThenSucceed() throws Exception {
      // given – first attempt throws ConnectException, second succeeds
      final byte[] successResponse = buildPublishResponse(ErrorCode.NONE, List.of(200L));
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")))
          .thenReturn(CompletableFuture.completedFuture(successResponse));

      // when
      final List<Long> positions = router.publishBatch(PARTITION_0, List.of(new byte[] {1})).get();

      // then
      assertThat(positions).containsExactly(200L);
      verify(messagingService, times(2))
          .sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldRetryOnLeaderUnavailableResponseThenSucceed() throws Exception {
      // given – first attempt returns LEADER_UNAVAILABLE, second succeeds
      final byte[] retryResponse = buildPublishResponse(ErrorCode.LEADER_UNAVAILABLE, List.of());
      final byte[] successResponse = buildPublishResponse(ErrorCode.NONE, List.of(300L));
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(retryResponse))
          .thenReturn(CompletableFuture.completedFuture(successResponse));

      // when
      final List<Long> positions = router.publishBatch(PARTITION_0, List.of(new byte[] {1})).get();

      // then
      assertThat(positions).containsExactly(300L);
      verify(messagingService, times(2))
          .sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldExhaustAllAttemptsAndFailWithBrokerException() {
      // given – all 4 attempts fail with ConnectException
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")));

      // when / then
      assertThatThrownBy(() -> router.publishBatch(PARTITION_0, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class);

      verify(messagingService, times(BrokerRequestRouter.MAX_ATTEMPTS))
          .sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldFailImmediatelyWhenNoLeaderKnownAfterAllAttempts() {
      // given – topology has no leader for this partition
      when(topologyService.getLeaderAddress(PARTITION_0)).thenReturn(Optional.empty());

      // when / then
      assertThatThrownBy(() -> router.publishBatch(PARTITION_0, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class);

      verify(messagingService, times(0)).sendAndReceive(any(), any(), any(), any(Duration.class));
    }

    @Test
    void shouldSurfaceNonLeaderUnavailableErrorCodes() {
      // given – broker returns PARTITION_NOT_FOUND
      final byte[] errorResponse = buildPublishResponse(ErrorCode.PARTITION_NOT_FOUND, List.of());
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(errorResponse));

      // when / then
      assertThatThrownBy(() -> router.publishBatch(PARTITION_0, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.PARTITION_NOT_FOUND);

      // should NOT retry for PARTITION_NOT_FOUND
      verify(messagingService, times(1))
          .sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.PRODUCE_REQUEST), any(), any(Duration.class));
    }
  }

  // -------------------------------------------------------------------------
  // Poll

  @Nested
  class Poll {

    @Test
    void shouldReturnEventsOnSuccess() throws Exception {
      // given
      final byte[] response =
          buildPollResponse(
              ErrorCode.NONE,
              /* nextPosition= */ 1002L,
              /* generation= */ 3L,
              List.of(new SbeCodec.PollEvent(1001L, new byte[] {42})),
              List.of());
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.FETCH_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final PollParams params = new PollParams(PARTITION_0, "grp", "c0", 1001L, 10, 0, 3L);
      final SbeCodec.PollResult result = router.poll(params).get();

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.nextPosition()).isEqualTo(1002L);
      assertThat(result.generation()).isEqualTo(3L);
      assertThat(result.events()).hasSize(1);
      assertThat(result.events().get(0).position()).isEqualTo(1001L);
      assertThat(result.events().get(0).payload()).containsExactly(42);
    }

    @Test
    void shouldReturnRebalanceInProgressWithoutError() throws Exception {
      // given – REBALANCE_IN_PROGRESS is not an error; the future should succeed
      final byte[] response =
          buildPollResponse(
              ErrorCode.REBALANCE_IN_PROGRESS,
              /* nextPosition= */ 0L,
              /* generation= */ 4L,
              List.of(),
              List.of(0, 1));
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.FETCH_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final SbeCodec.PollResult result =
          router.poll(new PollParams(PARTITION_0, "grp", "c0", 0L, 10, 0, 3L)).get();

      // then – future resolved normally, caller inspects the status
      assertThat(result.errorCode()).isEqualTo(ErrorCode.REBALANCE_IN_PROGRESS);
      assertThat(result.assignedPartitions()).containsExactly(0, 1);
    }

    @Test
    void shouldIncludeServerWaitMsInTimeout() throws Exception {
      // given
      final byte[] response = buildPollResponse(ErrorCode.NONE, 0L, 1L, List.of(), List.of());
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR), eq(MessageTypes.FETCH_REQUEST), any(), any(Duration.class)))
          .thenAnswer(
              inv -> {
                final Duration timeout = inv.getArgument(3);
                // Timeout must be at least serverWaitMs + slack
                assertThat(timeout.toMillis())
                    .isGreaterThanOrEqualTo(1000 + BrokerRequestRouter.POLL_TIMEOUT_SLACK_MS);
                return CompletableFuture.completedFuture(response);
              });

      // when
      router.poll(new PollParams(PARTITION_0, "grp", "c0", 0L, 10, 1000, 1L)).get();
    }
  }

  // -------------------------------------------------------------------------
  // GetLatestPosition

  @Nested
  class GetLatestPosition {

    @Test
    void shouldReturnPositionOnSuccess() throws Exception {
      // given
      final byte[] response = buildLatestPositionResponse(ErrorCode.NONE, 500L);
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR),
              eq(MessageTypes.LATEST_POSITION_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final long position = router.getLatestPosition(PARTITION_0).get();

      // then
      assertThat(position).isEqualTo(500L);
    }

    @Test
    void shouldRetryOnTransportErrorForLatestPosition() throws Exception {
      // given
      final byte[] response = buildLatestPositionResponse(ErrorCode.NONE, 42L);
      when(messagingService.sendAndReceive(
              eq(LEADER_ADDR),
              eq(MessageTypes.LATEST_POSITION_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final long position = router.getLatestPosition(PARTITION_0).get();

      // then
      assertThat(position).isEqualTo(42L);
      verify(messagingService, times(2))
          .sendAndReceive(
              any(), eq(MessageTypes.LATEST_POSITION_REQUEST), any(), any(Duration.class));
    }
  }

  // -------------------------------------------------------------------------
  // Subscribe (coordinator)

  @Nested
  class Subscribe {

    @Test
    void shouldReturnAssignedPartitionsOnSuccess() throws Exception {
      // given
      final byte[] response = buildSubscribeResponse(ErrorCode.NONE, 1L, List.of(0, 1));
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR), eq(MessageTypes.SUBSCRIBE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final SbeCodec.SubscribeResult result = router.subscribe("grp", "c0").get();

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.generation()).isEqualTo(1L);
      assertThat(result.assignedPartitions()).containsExactly(0, 1);
    }

    @Test
    void shouldRetryOnTransportErrorForSubscribe() throws Exception {
      // given
      final byte[] response = buildSubscribeResponse(ErrorCode.NONE, 2L, List.of(0));
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR), eq(MessageTypes.SUBSCRIBE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final SbeCodec.SubscribeResult result = router.subscribe("grp", "c0").get();

      // then
      assertThat(result.generation()).isEqualTo(2L);
      verify(messagingService, times(2))
          .sendAndReceive(any(), eq(MessageTypes.SUBSCRIBE_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldNotRetryOnCoordinatorUnavailableResponse() throws Exception {
      // given – COORDINATOR_UNAVAILABLE in response; should not retry (coordinator is down)
      final byte[] response =
          buildSubscribeResponse(ErrorCode.COORDINATOR_UNAVAILABLE, 0L, List.of());
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR), eq(MessageTypes.SUBSCRIBE_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when / then
      assertThatThrownBy(() -> router.subscribe("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);

      // should NOT retry since isRetryableResponse predicate returns false for coordinator ops
      verify(messagingService, times(1))
          .sendAndReceive(any(), eq(MessageTypes.SUBSCRIBE_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldFailWhenCoordinatorAddressUnknown() {
      // given
      when(topologyService.getCoordinatorAddress()).thenReturn(Optional.empty());

      // when / then
      assertThatThrownBy(() -> router.subscribe("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class);

      verify(messagingService, times(0)).sendAndReceive(any(), any(), any(), any(Duration.class));
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat (coordinator)

  @Nested
  class Heartbeat {

    @Test
    void shouldReturnGenerationOnSuccess() throws Exception {
      // given
      final byte[] response = buildHeartbeatResponse(ErrorCode.NONE, 5L);
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR), eq(MessageTypes.HEARTBEAT_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final long generation = router.heartbeat("grp", "c0").get();

      // then
      assertThat(generation).isEqualTo(5L);
    }

    @Test
    void shouldSurfaceConsumerNotRegistered() {
      // given
      final byte[] response = buildHeartbeatResponse(ErrorCode.CONSUMER_NOT_REGISTERED, 0L);
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR), eq(MessageTypes.HEARTBEAT_REQUEST), any(), any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when / then
      assertThatThrownBy(() -> router.heartbeat("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
    }
  }

  // -------------------------------------------------------------------------
  // CommitOffset (coordinator)

  @Nested
  class CommitOffset {

    @Test
    void shouldSucceedOnNoneErrorCode() throws Exception {
      // given
      final byte[] response = buildCommitResponse(ErrorCode.NONE);
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR),
              eq(MessageTypes.COMMIT_OFFSET_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when / then – no exception
      router.commitOffset(PARTITION_0, "grp", "c0", 1000L, 3L).get();
    }

    @Test
    void shouldSurfacePartitionNotFoundForCommit() {
      // given
      final byte[] response = buildCommitResponse(ErrorCode.PARTITION_NOT_FOUND);
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR),
              eq(MessageTypes.COMMIT_OFFSET_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when / then
      assertThatThrownBy(() -> router.commitOffset(PARTITION_0, "grp", "c0", 100L, 1L).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
    }
  }

  // -------------------------------------------------------------------------
  // FetchAssignment (coordinator)

  @Nested
  class FetchAssignment {

    @Test
    void shouldReturnAssignedPartitionsAndGenerationOnSuccess() throws Exception {
      // given
      final byte[] response = buildFetchAssignmentResponse(ErrorCode.NONE, 3L, List.of(0, 1, 2));
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR),
              eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final SbeCodec.FetchAssignmentResult result = router.fetchAssignment("grp", "c0").get();

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.generation()).isEqualTo(3L);
      assertThat(result.assignedPartitions()).containsExactly(0, 1, 2);
    }

    @Test
    void shouldRetryOnTransportErrorForFetchAssignment() throws Exception {
      // given – first attempt fails with ConnectException, second succeeds
      final byte[] response = buildFetchAssignmentResponse(ErrorCode.NONE, 5L, List.of(0));
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR),
              eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when
      final SbeCodec.FetchAssignmentResult result = router.fetchAssignment("grp", "c0").get();

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.generation()).isEqualTo(5L);
      assertThat(result.assignedPartitions()).containsExactly(0);
      verify(messagingService, times(2))
          .sendAndReceive(
              any(), eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldSurfaceCoordinatorUnavailableResponseWithoutRetry() {
      // given – coordinator responds with COORDINATOR_UNAVAILABLE (no retry for coordinator ops)
      final byte[] response =
          buildFetchAssignmentResponse(ErrorCode.COORDINATOR_UNAVAILABLE, 0L, List.of());
      when(messagingService.sendAndReceive(
              eq(COORDINATOR_ADDR),
              eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST),
              any(),
              any(Duration.class)))
          .thenReturn(CompletableFuture.completedFuture(response));

      // when / then
      assertThatThrownBy(() -> router.fetchAssignment("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);

      verify(messagingService, times(1))
          .sendAndReceive(
              any(), eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST), any(), any(Duration.class));
    }

    @Test
    void shouldFailWhenCoordinatorAddressUnknown() {
      // given
      when(topologyService.getCoordinatorAddress()).thenReturn(Optional.empty());

      // when / then
      assertThatThrownBy(() -> router.fetchAssignment("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);

      verify(messagingService, times(0)).sendAndReceive(any(), any(), any(), any(Duration.class));
    }
  }

  // -------------------------------------------------------------------------
  // No MessagingService (no AtomixCluster)

  @Nested
  class NoMessagingService {

    @Test
    void shouldFailFastWithLeaderUnavailableWhenNoClusterForPartitionOp() {
      // given – router with no AtomixCluster
      final BrokerRequestRouter routerWithoutCluster =
          new BrokerRequestRouter(/* cluster= */ null, topologyService);

      // when / then – partition operation returns LEADER_UNAVAILABLE
      assertThatThrownBy(
              () -> routerWithoutCluster.publishBatch(PARTITION_0, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.LEADER_UNAVAILABLE);
    }

    @Test
    void shouldFailFastWithCoordinatorUnavailableWhenNoClusterForSubscribe() {
      // given
      final BrokerRequestRouter routerWithoutCluster =
          new BrokerRequestRouter(/* cluster= */ null, topologyService);

      // when / then – coordinator operation must return COORDINATOR_UNAVAILABLE, not
      // LEADER_UNAVAILABLE
      assertThatThrownBy(() -> routerWithoutCluster.subscribe("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
    }

    @Test
    void shouldFailFastWithCoordinatorUnavailableWhenNoClusterForHeartbeat() {
      final BrokerRequestRouter routerWithoutCluster =
          new BrokerRequestRouter(/* cluster= */ null, topologyService);

      assertThatThrownBy(() -> routerWithoutCluster.heartbeat("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
    }

    @Test
    void shouldFailFastWithCoordinatorUnavailableWhenNoClusterForCommitOffset() {
      final BrokerRequestRouter routerWithoutCluster =
          new BrokerRequestRouter(/* cluster= */ null, topologyService);

      assertThatThrownBy(
              () -> routerWithoutCluster.commitOffset(PARTITION_0, "grp", "c0", 100L, 1L).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
    }

    @Test
    void shouldFailFastWithCoordinatorUnavailableWhenNoClusterForFetchAssignment() {
      final BrokerRequestRouter routerWithoutCluster =
          new BrokerRequestRouter(/* cluster= */ null, topologyService);

      assertThatThrownBy(() -> routerWithoutCluster.fetchAssignment("grp", "c0").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BrokerException.class)
          .extracting(ex -> ((BrokerException) ((ExecutionException) ex).getCause()).getErrorCode())
          .isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
    }
  }

  // -------------------------------------------------------------------------
  // isTransientTransportError helper

  @Nested
  class IsTransientTransportError {

    @Test
    void shouldRecogniseConnectException() {
      assertThat(BrokerRequestRouter.isTransientTransportError(new ConnectException())).isTrue();
    }

    @Test
    void shouldRecogniseTimeoutException() {
      assertThat(
              BrokerRequestRouter.isTransientTransportError(
                  new java.util.concurrent.TimeoutException()))
          .isTrue();
    }

    @Test
    void shouldRecogniseConnectionClosed() {
      assertThat(
              BrokerRequestRouter.isTransientTransportError(
                  new io.atomix.cluster.messaging.MessagingException.ConnectionClosed("closed")))
          .isTrue();
    }

    @Test
    void shouldRecogniseNoRemoteHandler() {
      assertThat(
              BrokerRequestRouter.isTransientTransportError(
                  new io.atomix.cluster.messaging.MessagingException.NoRemoteHandler("type")))
          .isTrue();
    }

    @Test
    void shouldNotRetryOnArbitraryException() {
      assertThat(BrokerRequestRouter.isTransientTransportError(new RuntimeException("unexpected")))
          .isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // SBE test helpers

  /** Builds a {@code PublishBatchResponse} byte array with the given positions. */
  private static byte[] buildPublishResponse(
      final ErrorCode errorCode, final List<Long> positions) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.PublishBatchResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.PublishBatchResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(64);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode);
    final var posGroup = encoder.positionsCount(positions.size());
    for (final long pos : positions) {
      posGroup.next().position(pos);
    }
    encoder.errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code PollResponse} byte array. */
  private static byte[] buildPollResponse(
      final ErrorCode errorCode,
      final long nextPosition,
      final long generation,
      final List<SbeCodec.PollEvent> events,
      final List<Integer> assignedPartitions) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.PollResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.PollResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(256);

    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(errorCode)
        .nextPosition(nextPosition)
        .generation(generation);

    final var evtGroup = encoder.eventsCount(events.size());
    for (final SbeCodec.PollEvent event : events) {
      evtGroup
          .next()
          .position(event.position())
          .putPayload(event.payload(), 0, event.payload().length);
    }

    final var apGroup = encoder.assignedPartitionsCount(assignedPartitions.size());
    for (final int partitionId : assignedPartitions) {
      apGroup.next().partitionId(partitionId);
    }
    encoder.errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code LatestPositionResponse} byte array. */
  private static byte[] buildLatestPositionResponse(
      final ErrorCode errorCode, final long position) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.LatestPositionResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.LatestPositionResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(64);

    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .position(position)
        .errorCode(errorCode)
        .errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code SubscribeResponse} byte array. */
  private static byte[] buildSubscribeResponse(
      final ErrorCode errorCode, final long generation, final List<Integer> partitions) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.SubscribeResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.SubscribeResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(128);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).generation(generation);

    final var apGroup = encoder.assignedPartitionsCount(partitions.size());
    for (final int partitionId : partitions) {
      apGroup.next().partitionId(partitionId);
    }
    encoder.errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code HeartbeatResponse} byte array. */
  private static byte[] buildHeartbeatResponse(final ErrorCode errorCode, final long generation) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.HeartbeatResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.HeartbeatResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(64);

    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(errorCode)
        .generation(generation)
        .errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code CommitOffsetResponse} byte array. */
  private static byte[] buildCommitResponse(final ErrorCode errorCode) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.CommitOffsetResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.CommitOffsetResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(64);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  /** Builds a {@code FetchAssignmentResponse} byte array. */
  private static byte[] buildFetchAssignmentResponse(
      final ErrorCode errorCode, final long generation, final List<Integer> partitions) {
    final io.camunda.eventbridge.core.protocol.MessageHeaderEncoder headerEncoder =
        new io.camunda.eventbridge.core.protocol.MessageHeaderEncoder();
    final io.camunda.eventbridge.core.protocol.FetchAssignmentResponseEncoder encoder =
        new io.camunda.eventbridge.core.protocol.FetchAssignmentResponseEncoder();
    final org.agrona.ExpandableArrayBuffer buf = new org.agrona.ExpandableArrayBuffer(128);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).generation(generation);

    final var apGroup = encoder.assignedPartitionsCount(partitions.size());
    for (final int partitionId : partitions) {
      apGroup.next().partitionId(partitionId);
    }
    encoder.errorMessage("");

    return copyBytes(
        buf,
        io.camunda.eventbridge.core.protocol.MessageHeaderEncoder.ENCODED_LENGTH
            + encoder.encodedLength());
  }

  private static byte[] copyBytes(final org.agrona.ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }
}
