/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.PollActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.core.protocol.CommitOffsetRequestEncoder;
import io.camunda.eventbridge.core.protocol.CommitOffsetResponseDecoder;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.protocol.FetchAssignmentRequestEncoder;
import io.camunda.eventbridge.core.protocol.FetchAssignmentResponseDecoder;
import io.camunda.eventbridge.core.protocol.HeartbeatRequestEncoder;
import io.camunda.eventbridge.core.protocol.HeartbeatResponseDecoder;
import io.camunda.eventbridge.core.protocol.LatestPositionRequestEncoder;
import io.camunda.eventbridge.core.protocol.LatestPositionResponseDecoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.core.protocol.PollRequestEncoder;
import io.camunda.eventbridge.core.protocol.PollResponseDecoder;
import io.camunda.eventbridge.core.protocol.PublishBatchRequestEncoder;
import io.camunda.eventbridge.core.protocol.PublishBatchResponseDecoder;
import io.camunda.eventbridge.core.protocol.SubscribeRequestEncoder;
import io.camunda.eventbridge.core.protocol.SubscribeResponseDecoder;
import io.camunda.eventbridge.core.transport.MessageTypes;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BrokerRequestDispatcher}.
 *
 * <p>Each nested class covers one handler method. Handlers are extracted via Mockito {@link
 * ArgumentCaptor} after calling {@link BrokerRequestDispatcher#start()}, then invoked directly as
 * functions to exercise the encode→dispatch→decode pipeline without a real Netty connection.
 *
 * <p>Request encoding and response decoding use the SBE-generated classes from {@code
 * event-bridge-core} directly — importing {@code SbeCodec} from the gateway module would create a
 * circular Maven dependency since the gateway already depends on the broker.
 */
@ExtendWith(MockitoExtension.class)
final class BrokerRequestDispatcherTest {

  private static final Address SENDER = Address.from("localhost", 9999);
  private static final int PARTITION_0 = 0;

  @Mock MessagingService messagingService;
  @Mock PublishActor publishActor;
  @Mock PollActor pollActor;
  @Mock CoordinatorActor coordinatorActor;

  BrokerRequestDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher =
        new BrokerRequestDispatcher(
            messagingService,
            Map.of(PARTITION_0, publishActor),
            Map.of(PARTITION_0, pollActor),
            coordinatorActor);
  }

  // ---------------------------------------------------------------------------
  // Lifecycle

  @Nested
  class Lifecycle {

    @Test
    void shouldRegisterAllHandlersOnStart() {
      dispatcher.start();

      verify(messagingService).registerHandler(eq(MessageTypes.PRODUCE_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.FETCH_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.LATEST_POSITION_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.SUBSCRIBE_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.HEARTBEAT_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.COMMIT_OFFSET_REQUEST), any());
      verify(messagingService).registerHandler(eq(MessageTypes.FETCH_ASSIGNMENT_REQUEST), any());
    }

    @Test
    void shouldUnregisterAllHandlersOnStop() {
      dispatcher.start();
      dispatcher.stop();

      verify(messagingService).unregisterHandler(MessageTypes.PRODUCE_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.FETCH_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.LATEST_POSITION_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.SUBSCRIBE_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.HEARTBEAT_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.COMMIT_OFFSET_REQUEST);
      verify(messagingService).unregisterHandler(MessageTypes.FETCH_ASSIGNMENT_REQUEST);
    }
  }

  // ---------------------------------------------------------------------------
  // Publish (PRODUCE_REQUEST)

  @Nested
  class Publish {

    @Test
    void shouldReturnPositionsOnSuccess() throws Exception {
      // given
      final byte[] req = encodePublishBatch(PARTITION_0, List.of(new byte[] {1, 2, 3}));
      final var future = new CompletableActorFuture<List<Long>>();
      future.complete(List.of(10L));
      when(publishActor.publishBatch(any())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.PRODUCE_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePublishBatchResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.positions()).containsExactly(10L);
    }

    @Test
    void shouldReturnPartitionNotFoundForUnknownPartition() throws Exception {
      // given – partition 99 not registered
      final byte[] req = encodePublishBatch(99, List.of(new byte[] {1}));

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.PRODUCE_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePublishBatchResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
      verify(publishActor, never()).publishBatch(any());
    }

    @Test
    void shouldReturnLeaderUnavailableOnActorFailure() throws Exception {
      // given
      final byte[] req = encodePublishBatch(PARTITION_0, List.of(new byte[] {1}));
      final var future = new CompletableActorFuture<List<Long>>();
      future.completeExceptionally(new RuntimeException("disk full"));
      when(publishActor.publishBatch(any())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.PRODUCE_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePublishBatchResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.LEADER_UNAVAILABLE);
      assertThat(decoded.errorMessage()).contains("disk full");
    }
  }

  // ---------------------------------------------------------------------------
  // Poll (FETCH_REQUEST)

  @Nested
  class Poll {

    @Test
    void shouldReturnEventsOnSuccess() throws Exception {
      // given
      final byte[] req = encodePollRequest(PARTITION_0, "g1", "c1", 100L, 10, 0, 1L);
      final var future = new CompletableActorFuture<PollActor.PollResult>();
      future.complete(
          new PollActor.PollResult(
              List.of(new PollActor.PollEvent(101L, new byte[] {0x42})), 102L));
      when(pollActor.poll(anyLong(), anyInt(), anyInt())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.FETCH_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePollResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.nextPosition()).isEqualTo(102L);
      assertThat(decoded.events()).hasSize(1);
      assertThat(decoded.events().get(0).position()).isEqualTo(101L);
      assertThat(decoded.events().get(0).payload()).containsExactly(0x42);
    }

    @Test
    void shouldReturnPartitionNotFoundForUnknownPartition() throws Exception {
      // given
      final byte[] req = encodePollRequest(99, "g1", "c1", 0L, 10, 0, 1L);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.FETCH_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePollResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
    }

    @Test
    void shouldReturnPositionTruncatedOnTruncation() throws Exception {
      // given
      final byte[] req = encodePollRequest(PARTITION_0, "g1", "c1", 5L, 10, 0, 1L);
      final var future = new CompletableActorFuture<PollActor.PollResult>();
      future.completeExceptionally(new PollActor.PositionTruncatedException(100L));
      when(pollActor.poll(anyLong(), anyInt(), anyInt())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.FETCH_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodePollResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.POSITION_TRUNCATED);
      assertThat(decoded.errorMessage()).contains("100");
    }
  }

  // ---------------------------------------------------------------------------
  // LatestPosition (LATEST_POSITION_REQUEST)

  @Nested
  class LatestPosition {

    @Test
    void shouldReturnPositionOnSuccess() throws Exception {
      // given
      final byte[] req = encodeLatestPositionRequest(PARTITION_0);
      final var future = new CompletableActorFuture<Long>();
      future.complete(500L);
      when(pollActor.getLatestPosition()).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.LATEST_POSITION_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeLatestPositionResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.position()).isEqualTo(500L);
    }

    @Test
    void shouldReturnPartitionNotFoundForUnknownPartition() throws Exception {
      // given
      final byte[] req = encodeLatestPositionRequest(99);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.LATEST_POSITION_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeLatestPositionResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
    }
  }

  // ---------------------------------------------------------------------------
  // Subscribe (SUBSCRIBE_REQUEST)

  @Nested
  class Subscribe {

    @Test
    void shouldReturnAssignedPartitionsOnSuccess() throws Exception {
      // given
      final byte[] req = encodeSubscribeRequest("grp1", "cons1");
      final var future = new CompletableActorFuture<CoordinatorActor.SubscribeResult>();
      future.complete(new CoordinatorActor.SubscribeResult(List.of(0, 1), 3L));
      when(coordinatorActor.subscribe(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.SUBSCRIBE_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeSubscribeResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.generation()).isEqualTo(3L);
      assertThat(decoded.assignedPartitions()).containsExactly(0, 1);
    }

    @Test
    void shouldReturnCoordinatorUnavailableOnActorFailure() throws Exception {
      // given
      final byte[] req = encodeSubscribeRequest("grp1", "cons1");
      final var future = new CompletableActorFuture<CoordinatorActor.SubscribeResult>();
      future.completeExceptionally(new RuntimeException("coordinator down"));
      when(coordinatorActor.subscribe(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.SUBSCRIBE_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeSubscribeResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
    }
  }

  // ---------------------------------------------------------------------------
  // Heartbeat (HEARTBEAT_REQUEST)

  @Nested
  class Heartbeat {

    @Test
    void shouldReturnGenerationOnSuccess() throws Exception {
      // given
      final byte[] req = encodeHeartbeatRequest("grp1", "cons1");
      final var future = new CompletableActorFuture<Long>();
      future.complete(7L);
      when(coordinatorActor.heartbeat(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.HEARTBEAT_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeHeartbeatResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.generation()).isEqualTo(7L);
    }

    @Test
    void shouldReturnConsumerNotRegisteredOnDeadConsumer() throws Exception {
      // given
      final byte[] req = encodeHeartbeatRequest("grp1", "dead");
      final var future = new CompletableActorFuture<Long>();
      future.completeExceptionally(
          new CoordinatorActor.ConsumerNotRegisteredException("grp1", "dead"));
      when(coordinatorActor.heartbeat(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.HEARTBEAT_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeHeartbeatResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
    }
  }

  // ---------------------------------------------------------------------------
  // CommitOffset (COMMIT_OFFSET_REQUEST)

  @Nested
  class CommitOffset {

    @Test
    void shouldReturnOkOnSuccess() throws Exception {
      // given
      final byte[] req = encodeCommitOffsetRequest(PARTITION_0, "grp1", "cons1", 99L, 2L);
      final var future = new CompletableActorFuture<Void>();
      future.complete(null);
      when(coordinatorActor.commitOffset(anyString(), anyString(), anyInt(), anyLong()))
          .thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.COMMIT_OFFSET_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeCommitOffsetResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
    }

    @Test
    void shouldReturnConsumerNotRegisteredOnDeadConsumer() throws Exception {
      // given
      final byte[] req = encodeCommitOffsetRequest(PARTITION_0, "grp1", "dead", 99L, 2L);
      final var future = new CompletableActorFuture<Void>();
      future.completeExceptionally(
          new CoordinatorActor.ConsumerNotRegisteredException("grp1", "dead"));
      when(coordinatorActor.commitOffset(anyString(), anyString(), anyInt(), anyLong()))
          .thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.COMMIT_OFFSET_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeCommitOffsetResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
    }
  }

  // ---------------------------------------------------------------------------
  // FetchAssignment (FETCH_ASSIGNMENT_REQUEST)

  @Nested
  class FetchAssignment {

    @Test
    void shouldReturnAssignmentOnSuccess() throws Exception {
      // given
      final byte[] req = encodeFetchAssignmentRequest("grp1", "cons1");
      final var future = new CompletableActorFuture<CoordinatorActor.AssignmentResult>();
      future.complete(new CoordinatorActor.AssignmentResult(List.of(0), 5L));
      when(coordinatorActor.getAssignment(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.FETCH_ASSIGNMENT_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeFetchAssignmentResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(decoded.generation()).isEqualTo(5L);
      assertThat(decoded.assignedPartitions()).containsExactly(0);
    }

    @Test
    void shouldReturnConsumerNotRegisteredWhenGroupUnknown() throws Exception {
      // given
      final byte[] req = encodeFetchAssignmentRequest("unknown-group", "cons1");
      final var future = new CompletableActorFuture<CoordinatorActor.AssignmentResult>();
      future.completeExceptionally(
          new CoordinatorActor.ConsumerNotRegisteredException("unknown-group", "cons1"));
      when(coordinatorActor.getAssignment(anyString(), anyString())).thenReturn(future);

      // when
      final byte[] responseBytes =
          captureHandler(MessageTypes.FETCH_ASSIGNMENT_REQUEST).apply(SENDER, req).get();

      // then
      final var decoded = decodeFetchAssignmentResponse(responseBytes);
      assertThat(decoded.errorCode()).isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers — handler capture

  /**
   * Starts the dispatcher, captures the handler registered for {@code messageType}, and returns it
   * as a {@code BiFunction<Address, byte[], CompletableFuture<byte[]>>} for direct invocation.
   */
  @SuppressWarnings("unchecked")
  private BiFunction<Address, byte[], CompletableFuture<byte[]>> captureHandler(
      final String messageType) {
    dispatcher.start();
    final ArgumentCaptor<BiFunction<Address, byte[], CompletableFuture<byte[]>>> captor =
        ArgumentCaptor.forClass(BiFunction.class);
    verify(messagingService).registerHandler(eq(messageType), captor.capture());
    return captor.getValue();
  }

  // ---------------------------------------------------------------------------
  // Helpers — request encoders (gateway-side encoding using raw SBE classes from event-bridge-core)

  private static byte[] encodePublishBatch(final int partitionId, final List<byte[]> payloads) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(512);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PublishBatchRequestEncoder encoder = new PublishBatchRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).partitionId(partitionId);
    final PublishBatchRequestEncoder.EventsEncoder evts = encoder.eventsCount(payloads.size());
    for (final byte[] payload : payloads) {
      evts.next().putPayload(payload, 0, payload.length);
    }
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodePollRequest(
      final int partitionId,
      final String groupId,
      final String consumerId,
      final long fromPosition,
      final int maxRecords,
      final int serverWaitMs,
      final long generation) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(512);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PollRequestEncoder encoder = new PollRequestEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .partitionId(partitionId)
        .fromPosition(fromPosition)
        .maxRecords(maxRecords)
        .serverWaitMs(serverWaitMs)
        .generation(generation)
        .groupId(groupId)
        .consumerId(consumerId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodeLatestPositionRequest(final int partitionId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(64);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final LatestPositionRequestEncoder encoder = new LatestPositionRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).partitionId(partitionId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodeSubscribeRequest(final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final SubscribeRequestEncoder encoder = new SubscribeRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodeHeartbeatRequest(final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final HeartbeatRequestEncoder encoder = new HeartbeatRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodeCommitOffsetRequest(
      final int partitionId,
      final String groupId,
      final String consumerId,
      final long position,
      final long generation) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final CommitOffsetRequestEncoder encoder = new CommitOffsetRequestEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .partitionId(partitionId)
        .position(position)
        .generation(generation)
        .groupId(groupId)
        .consumerId(consumerId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] encodeFetchAssignmentRequest(
      final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final FetchAssignmentRequestEncoder encoder = new FetchAssignmentRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Helpers — response decoders (broker response decoded back to gateway-side view)

  private record PublishBatchResponse(
      ErrorCode errorCode, List<Long> positions, String errorMessage) {}

  private record PollEventRecord(long position, byte[] payload) {}

  private record PollResponse(
      ErrorCode errorCode, long nextPosition, List<PollEventRecord> events, String errorMessage) {}

  private record LatestPositionResponse(ErrorCode errorCode, long position) {}

  private record SubscribeResponse(
      ErrorCode errorCode, long generation, List<Integer> assignedPartitions) {}

  private record HeartbeatResponse(ErrorCode errorCode, long generation) {}

  private record CommitOffsetResponse(ErrorCode errorCode) {}

  private record FetchAssignmentResponse(
      ErrorCode errorCode, long generation, List<Integer> assignedPartitions) {}

  private static PublishBatchResponse decodePublishBatchResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PublishBatchResponseDecoder decoder = new PublishBatchResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final List<Long> positions = new ArrayList<>();
    for (final PublishBatchResponseDecoder.PositionsDecoder pos : decoder.positions()) {
      positions.add(pos.position());
    }
    return new PublishBatchResponse(decoder.errorCode(), positions, decoder.errorMessage());
  }

  private static PollResponse decodePollResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PollResponseDecoder decoder = new PollResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final List<PollEventRecord> events = new ArrayList<>();
    for (final PollResponseDecoder.EventsDecoder evt : decoder.events()) {
      final int len = evt.payloadLength();
      final byte[] payload = new byte[len];
      evt.getPayload(payload, 0, len);
      events.add(new PollEventRecord(evt.position(), payload));
    }
    // Must consume assignedPartitions group before reading errorMessage var-data.
    decoder.assignedPartitions();
    return new PollResponse(
        decoder.errorCode(), decoder.nextPosition(), events, decoder.errorMessage());
  }

  private static LatestPositionResponse decodeLatestPositionResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final LatestPositionResponseDecoder decoder = new LatestPositionResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);
    return new LatestPositionResponse(decoder.errorCode(), decoder.position());
  }

  private static SubscribeResponse decodeSubscribeResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final SubscribeResponseDecoder decoder = new SubscribeResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final List<Integer> partitions = new ArrayList<>();
    for (final SubscribeResponseDecoder.AssignedPartitionsDecoder ap :
        decoder.assignedPartitions()) {
      partitions.add(ap.partitionId());
    }
    return new SubscribeResponse(decoder.errorCode(), decoder.generation(), partitions);
  }

  private static HeartbeatResponse decodeHeartbeatResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final HeartbeatResponseDecoder decoder = new HeartbeatResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);
    return new HeartbeatResponse(decoder.errorCode(), decoder.generation());
  }

  private static CommitOffsetResponse decodeCommitOffsetResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final CommitOffsetResponseDecoder decoder = new CommitOffsetResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);
    return new CommitOffsetResponse(decoder.errorCode());
  }

  private static FetchAssignmentResponse decodeFetchAssignmentResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final FetchAssignmentResponseDecoder decoder = new FetchAssignmentResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final List<Integer> partitions = new ArrayList<>();
    for (final FetchAssignmentResponseDecoder.AssignedPartitionsDecoder ap :
        decoder.assignedPartitions()) {
      partitions.add(ap.partitionId());
    }
    return new FetchAssignmentResponse(decoder.errorCode(), decoder.generation(), partitions);
  }

  private static byte[] copyBytes(final ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }
}
