/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport;

import io.camunda.eventbridge.core.EventDataBatch;
import io.camunda.eventbridge.core.protocol.AckRequestDecoder;
import io.camunda.eventbridge.core.protocol.AckResponseEncoder;
import io.camunda.eventbridge.core.protocol.CommitOffsetRequestDecoder;
import io.camunda.eventbridge.core.protocol.CommitOffsetResponseEncoder;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.protocol.FetchAssignmentRequestDecoder;
import io.camunda.eventbridge.core.protocol.FetchAssignmentResponseEncoder;
import io.camunda.eventbridge.core.protocol.HeartbeatRequestDecoder;
import io.camunda.eventbridge.core.protocol.HeartbeatResponseEncoder;
import io.camunda.eventbridge.core.protocol.LatestPositionRequestDecoder;
import io.camunda.eventbridge.core.protocol.LatestPositionResponseEncoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.core.protocol.PollRequestDecoder;
import io.camunda.eventbridge.core.protocol.PollResponseEncoder;
import io.camunda.eventbridge.core.protocol.PublishBatchRequestDecoder;
import io.camunda.eventbridge.core.protocol.PublishBatchResponseEncoder;
import io.camunda.eventbridge.core.protocol.SubscribeRequestDecoder;
import io.camunda.eventbridge.core.protocol.SubscribeResponseEncoder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Stateless utility class providing SBE encode/decode for the <em>broker side</em> of the Event
 * Bridge gateway↔broker protocol.
 *
 * <p>Mirrors {@code SbeCodec} in {@code event-bridge-gateway} but from the broker's perspective:
 * decodes <em>request</em> messages received from the gateway and encodes <em>response</em>
 * messages sent back.
 *
 * <p>Each method creates fresh codec instances; this class is thread-safe.
 */
public final class BrokerSbeCodec {

  private static final int INITIAL_BUFFER_SIZE = 512;

  private BrokerSbeCodec() {}

  // ---------------------------------------------------------------------------
  // PublishBatch

  /** Decoded content of a {@code PublishBatchRequest}. */
  public record PublishBatchRequest(int partitionId, EventDataBatch eventBatch) {}

  /**
   * Decodes a {@code PublishBatchRequest} SBE message.
   *
   * @param bytes the raw message bytes (header + body)
   * @return decoded request
   */
  public static PublishBatchRequest decodePublishBatch(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PublishBatchRequestDecoder decoder = new PublishBatchRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final int partitionId = decoder.partitionId();
    final int batchLen = decoder.eventBatchLength();
    final byte[] batchBytes = new byte[batchLen];
    decoder.getEventBatch(batchBytes, 0, batchLen);
    return new PublishBatchRequest(partitionId, EventDataBatch.fromBytes(batchBytes));
  }

  /**
   * Encodes a successful {@code PublishBatchResponse} SBE message.
   *
   * @param positions the log positions assigned to each event in order
   * @return fully-framed byte array
   */
  public static byte[] encodePublishBatchSuccess(final List<Long> positions) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PublishBatchResponseEncoder encoder = new PublishBatchResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(ErrorCode.NONE);

    final PublishBatchResponseEncoder.PositionsEncoder posEnc =
        encoder.positionsCount(positions.size());
    for (final long pos : positions) {
      posEnc.next().position(pos);
    }
    encoder.errorMessage("");
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /**
   * Encodes an error {@code PublishBatchResponse} SBE message.
   *
   * @param errorCode the error code
   * @param message human-readable error detail
   * @return fully-framed byte array
   */
  public static byte[] encodePublishBatchError(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PublishBatchResponseEncoder encoder = new PublishBatchResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode);
    encoder.positionsCount(0);
    encoder.errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Poll

  /** Decoded content of a {@code PollRequest}. */
  public record PollRequest(
      int partitionId,
      long fromPosition,
      int maxRecords,
      int serverWaitMs,
      long generation,
      String groupId,
      String consumerId) {}

  /**
   * Decodes a {@code PollRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   */
  public static PollRequest decodePoll(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PollRequestDecoder decoder = new PollRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new PollRequest(
        decoder.partitionId(),
        decoder.fromPosition(),
        decoder.maxRecords(),
        decoder.serverWaitMs(),
        decoder.generation(),
        decoder.groupId(),
        decoder.consumerId());
  }

  /** A single event in a poll response. */
  public record PollResponseEvent(long position, byte[] payload) {}

  /**
   * Encodes a successful {@code PollResponse} SBE message (normal records or empty).
   *
   * @param events the events to include (may be empty)
   * @param nextPosition position for the next poll call
   * @param generation current rebalance generation
   * @return fully-framed byte array
   */
  public static byte[] encodePollSuccess(
      final List<PollResponseEvent> events, final long nextPosition, final long generation) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PollResponseEncoder encoder = new PollResponseEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(ErrorCode.NONE)
        .nextPosition(nextPosition)
        .generation(generation);

    // SBE groups must be written in declaration order: events first, then assignedPartitions.
    final PollResponseEncoder.EventsEncoder eventsEnc = encoder.eventsCount(events.size());
    for (final PollResponseEvent evt : events) {
      eventsEnc.next().position(evt.position()).putPayload(evt.payload(), 0, evt.payload().length);
    }
    encoder.assignedPartitionsCount(0);
    encoder.errorMessage("");
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /**
   * Encodes an error {@code PollResponse} SBE message.
   *
   * @param errorCode the error code
   * @param message human-readable error detail
   * @return fully-framed byte array
   */
  public static byte[] encodePollError(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PollResponseEncoder encoder = new PollResponseEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(errorCode)
        .nextPosition(0L)
        .generation(0L);
    encoder.eventsCount(0);
    encoder.assignedPartitionsCount(0);
    encoder.errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // CommitOffset

  /** Decoded content of a {@code CommitOffsetRequest}. */
  public record CommitOffsetRequest(
      int partitionId, long position, String groupId, String consumerId) {}

  /**
   * Decodes a {@code CommitOffsetRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   */
  public static CommitOffsetRequest decodeCommitOffset(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final CommitOffsetRequestDecoder decoder = new CommitOffsetRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new CommitOffsetRequest(
        decoder.partitionId(), decoder.position(), decoder.groupId(), decoder.consumerId());
  }

  /**
   * Encodes a {@code CommitOffsetResponse} SBE message.
   *
   * @param errorCode error code ({@link ErrorCode#NONE} for success)
   * @param message error message (empty string for success)
   * @return fully-framed byte array
   */
  public static byte[] encodeCommitOffset(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final CommitOffsetResponseEncoder encoder = new CommitOffsetResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Heartbeat

  /** Decoded content of a {@code HeartbeatRequest}. */
  public record HeartbeatRequest(
      String groupId, String consumerId, long epoch, List<Integer> ownedPartitions) {}

  /**
   * Decodes a {@code HeartbeatRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   */
  public static HeartbeatRequest decodeHeartbeat(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final HeartbeatRequestDecoder decoder = new HeartbeatRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final long epoch = decoder.epoch();

    final List<Integer> ownedPartitions = new ArrayList<>();
    for (final var partition : decoder.ownedPartitions()) {
      ownedPartitions.add(partition.partitionId());
    }

    return new HeartbeatRequest(decoder.groupId(), decoder.consumerId(), epoch, ownedPartitions);
  }

  /**
   * Encodes a {@code HeartbeatResponse} SBE message.
   *
   * @param errorCode error code ({@link ErrorCode#NONE} for success)
   * @param epoch current rebalance epoch (0 on error)
   * @param revoke partitions the client should stop consuming (empty on error)
   * @param assign partitions the client should start consuming (empty on error)
   * @param fullAssignment complete authoritative assignment list (empty unless full reconcile)
   * @param message error message (empty string for success)
   * @return fully-framed byte array
   */
  public static byte[] encodeHeartbeat(
      final ErrorCode errorCode,
      final long epoch,
      final List<Integer> revoke,
      final List<Integer> assign,
      final List<Integer> fullAssignment,
      final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final HeartbeatResponseEncoder encoder = new HeartbeatResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).epoch(epoch);
    final HeartbeatResponseEncoder.RevokeEncoder revokeEnc = encoder.revokeCount(revoke.size());
    for (final int p : revoke) {
      revokeEnc.next().partitionId(p);
    }
    final HeartbeatResponseEncoder.AssignEncoder assignEnc = encoder.assignCount(assign.size());
    for (final int p : assign) {
      assignEnc.next().partitionId(p);
    }
    final HeartbeatResponseEncoder.FullAssignmentEncoder fullEnc =
        encoder.fullAssignmentCount(fullAssignment.size());
    for (final int p : fullAssignment) {
      fullEnc.next().partitionId(p);
    }
    encoder.errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Subscribe (DEPRECATED — handler removed from BrokerRequestDispatcher; Phase 5 deletes these)

  /**
   * @deprecated Subscribe protocol replaced by heartbeat auto-registration.
   */
  @Deprecated
  public record SubscribeRequest(String groupId, String consumerId) {}

  /**
   * Decodes a {@code SubscribeRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   * @deprecated Subscribe protocol replaced by heartbeat auto-registration.
   */
  @Deprecated
  public static SubscribeRequest decodeSubscribe(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final SubscribeRequestDecoder decoder = new SubscribeRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new SubscribeRequest(decoder.groupId(), decoder.consumerId());
  }

  /**
   * Encodes a successful {@code SubscribeResponse} SBE message.
   *
   * @param generation the new rebalance generation
   * @param assignedPartitions the partitions assigned to this consumer
   * @return fully-framed byte array
   * @deprecated Subscribe protocol replaced by heartbeat auto-registration.
   */
  @Deprecated
  public static byte[] encodeSubscribeSuccess(
      final long generation, final List<Integer> assignedPartitions) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final SubscribeResponseEncoder encoder = new SubscribeResponseEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(ErrorCode.NONE)
        .generation(generation);

    final SubscribeResponseEncoder.AssignedPartitionsEncoder apEnc =
        encoder.assignedPartitionsCount(assignedPartitions.size());
    for (final int partitionId : assignedPartitions) {
      apEnc.next().partitionId(partitionId);
    }
    encoder.errorMessage("");
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /**
   * Encodes an error {@code SubscribeResponse} SBE message.
   *
   * @param errorCode the error code
   * @param message human-readable error detail
   * @return fully-framed byte array
   * @deprecated Subscribe protocol replaced by heartbeat auto-registration.
   */
  @Deprecated
  public static byte[] encodeSubscribeError(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final SubscribeResponseEncoder encoder = new SubscribeResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).generation(0L);
    encoder.assignedPartitionsCount(0);
    encoder.errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // FetchAssignment

  /** Decoded content of a {@code FetchAssignmentRequest}. */
  public record FetchAssignmentRequest(String groupId, String consumerId) {}

  /**
   * Decodes a {@code FetchAssignmentRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   */
  public static FetchAssignmentRequest decodeFetchAssignment(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final FetchAssignmentRequestDecoder decoder = new FetchAssignmentRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new FetchAssignmentRequest(decoder.groupId(), decoder.consumerId());
  }

  /**
   * Encodes a successful {@code FetchAssignmentResponse} SBE message.
   *
   * @param generation the current rebalance generation
   * @param assignedPartitions the partitions assigned to this consumer
   * @return fully-framed byte array
   */
  public static byte[] encodeFetchAssignmentSuccess(
      final long generation, final List<Integer> assignedPartitions) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final FetchAssignmentResponseEncoder encoder = new FetchAssignmentResponseEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(ErrorCode.NONE)
        .generation(generation);

    final FetchAssignmentResponseEncoder.AssignedPartitionsEncoder apEnc =
        encoder.assignedPartitionsCount(assignedPartitions.size());
    for (final int partitionId : assignedPartitions) {
      apEnc.next().partitionId(partitionId);
    }
    encoder.errorMessage("");
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /**
   * Encodes an error {@code FetchAssignmentResponse} SBE message.
   *
   * @param errorCode the error code
   * @param message human-readable error detail
   * @return fully-framed byte array
   */
  public static byte[] encodeFetchAssignmentError(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final FetchAssignmentResponseEncoder encoder = new FetchAssignmentResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).generation(0L);
    encoder.assignedPartitionsCount(0);
    encoder.errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Ack

  /** Decoded content of an {@code AckRequest}. */
  public record AckRequest(
      String groupId,
      String consumerId,
      long epoch,
      List<Integer> revoked,
      List<Integer> assigned) {}

  /**
   * Decodes an {@code AckRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return decoded request
   */
  public static AckRequest decodeAck(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final AckRequestDecoder decoder = new AckRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final long epoch = decoder.epoch();

    final List<Integer> revoked = new ArrayList<>();
    for (final var r : decoder.revoked()) {
      revoked.add(r.partitionId());
    }

    final List<Integer> assigned = new ArrayList<>();
    for (final var a : decoder.assigned()) {
      assigned.add(a.partitionId());
    }

    return new AckRequest(decoder.groupId(), decoder.consumerId(), epoch, revoked, assigned);
  }

  /**
   * Encodes an {@code AckResponse} SBE message.
   *
   * @param errorCode error code ({@link ErrorCode#NONE} for success)
   * @param message error message (empty string for success)
   * @return fully-framed byte array
   */
  public static byte[] encodeAckResponse(final ErrorCode errorCode, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final AckResponseEncoder encoder = new AckResponseEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // LatestPosition

  /**
   * Decodes a {@code LatestPositionRequest} SBE message.
   *
   * @param bytes the raw message bytes
   * @return the partitionId extracted from the request
   */
  public static int decodeLatestPositionRequest(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final LatestPositionRequestDecoder decoder = new LatestPositionRequestDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);
    return decoder.partitionId();
  }

  /**
   * Encodes a {@code LatestPositionResponse} SBE message.
   *
   * @param errorCode error code ({@link ErrorCode#NONE} for success)
   * @param position the latest committed log position (0 if error)
   * @param message error message (empty string for success)
   * @return fully-framed byte array
   */
  public static byte[] encodeLatestPosition(
      final ErrorCode errorCode, final long position, final String message) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(64);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final LatestPositionResponseEncoder encoder = new LatestPositionResponseEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .errorCode(errorCode)
        .position(position)
        .errorMessage(message);
    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // ---------------------------------------------------------------------------
  // Helpers

  /** Copies the first {@code length} bytes from the buffer into a new array. */
  private static byte[] copyBytes(final ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }
}
