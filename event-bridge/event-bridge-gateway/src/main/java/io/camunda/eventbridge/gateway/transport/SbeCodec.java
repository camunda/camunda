/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import io.camunda.eventbridge.core.EventDataBatch;
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
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Stateless utility class providing SBE encode/decode for all Event Bridge gateway↔broker protocol
 * messages.
 *
 * <p>Each method creates fresh codec instances; this class is thread-safe.
 */
public final class SbeCodec {

  private static final int INITIAL_BUFFER_SIZE = 512;

  private SbeCodec() {}

  // -------------------------------------------------------------------------
  // PublishBatch

  /**
   * Encodes a {@code PublishBatchRequest} SBE message.
   *
   * @param partitionId target partition
   * @param batch the event batch to publish
   * @return fully-framed byte array (header + body)
   */
  public static byte[] encodePublishBatch(final int partitionId, final EventDataBatch batch) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PublishBatchRequestEncoder encoder = new PublishBatchRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).partitionId(partitionId);

    final byte[] batchBytes = batch.toBytes();
    encoder.putEventBatch(batchBytes, 0, batchBytes.length);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** Decoded result of a {@code PublishBatchResponse}. */
  public record PublishBatchResult(
      ErrorCode errorCode, List<Long> positions, String errorMessage) {}

  /**
   * Decodes a {@code PublishBatchResponse} SBE message.
   *
   * @param bytes the raw response bytes (header + body)
   * @return decoded result
   */
  public static PublishBatchResult decodePublishBatch(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PublishBatchResponseDecoder decoder = new PublishBatchResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final ErrorCode errorCode = decoder.errorCode();
    final List<Long> positions = new ArrayList<>();
    for (final PublishBatchResponseDecoder.PositionsDecoder pos : decoder.positions()) {
      positions.add(pos.position());
    }
    final String errorMessage = decoder.errorMessage();
    return new PublishBatchResult(errorCode, positions, errorMessage);
  }

  // -------------------------------------------------------------------------
  // PollRequest / PollResponse

  /** Poll request parameters. */
  public record PollParams(
      int partitionId,
      String groupId,
      String consumerId,
      long fromPosition,
      int maxRecords,
      int serverWaitMs,
      long generation) {}

  /**
   * Encodes a {@code PollRequest} SBE message.
   *
   * @param params all poll parameters
   * @return fully-framed byte array
   */
  public static byte[] encodePollRequest(final PollParams params) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final PollRequestEncoder encoder = new PollRequestEncoder();
    encoder
        .wrapAndApplyHeader(buf, 0, headerEncoder)
        .partitionId(params.partitionId())
        .fromPosition(params.fromPosition())
        .maxRecords(params.maxRecords())
        .serverWaitMs(params.serverWaitMs())
        .generation(params.generation())
        .groupId(params.groupId())
        .consumerId(params.consumerId());

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** A single event returned in a poll response. */
  public record PollEvent(long position, byte[] payload) {}

  /** Decoded result of a {@code PollResponse}. */
  public record PollResult(
      ErrorCode errorCode,
      long nextPosition,
      long generation,
      List<PollEvent> events,
      List<Integer> assignedPartitions,
      String errorMessage) {}

  /**
   * Decodes a {@code PollResponse} SBE message.
   *
   * <p>SBE groups must be accessed in declaration order: {@code events} then {@code
   * assignedPartitions}.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static PollResult decodePollResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final PollResponseDecoder decoder = new PollResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final ErrorCode errorCode = decoder.errorCode();
    final long nextPosition = decoder.nextPosition();
    final long generation = decoder.generation();

    // Groups must be decoded in declaration order: events first, then assignedPartitions.
    final List<PollEvent> events = new ArrayList<>();
    for (final PollResponseDecoder.EventsDecoder evt : decoder.events()) {
      final int len = evt.payloadLength();
      final byte[] payload = new byte[len];
      evt.getPayload(payload, 0, len);
      events.add(new PollEvent(evt.position(), payload));
    }

    final List<Integer> assignedPartitions = new ArrayList<>();
    for (final PollResponseDecoder.AssignedPartitionsDecoder ap : decoder.assignedPartitions()) {
      assignedPartitions.add(ap.partitionId());
    }

    final String errorMessage = decoder.errorMessage();
    return new PollResult(
        errorCode, nextPosition, generation, events, assignedPartitions, errorMessage);
  }

  // -------------------------------------------------------------------------
  // CommitOffset

  /**
   * Encodes a {@code CommitOffsetRequest} SBE message.
   *
   * @param partitionId target partition
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @param position offset to commit
   * @param generation current rebalance generation
   * @return fully-framed byte array
   */
  public static byte[] encodeCommitOffset(
      final int partitionId,
      final String groupId,
      final String consumerId,
      final long position,
      final long generation) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
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

  /** Decoded result of a {@code CommitOffsetResponse}. */
  public record CommitResult(ErrorCode errorCode, String errorMessage) {}

  /**
   * Decodes a {@code CommitOffsetResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static CommitResult decodeCommitOffset(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final CommitOffsetResponseDecoder decoder = new CommitOffsetResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new CommitResult(decoder.errorCode(), decoder.errorMessage());
  }

  // -------------------------------------------------------------------------
  // Subscribe

  /**
   * Encodes a {@code SubscribeRequest} SBE message.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @return fully-framed byte array
   */
  public static byte[] encodeSubscribe(final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final SubscribeRequestEncoder encoder = new SubscribeRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** Decoded result of a {@code SubscribeResponse}. */
  public record SubscribeResult(
      ErrorCode errorCode,
      long generation,
      List<Integer> assignedPartitions,
      String errorMessage) {}

  /**
   * Decodes a {@code SubscribeResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static SubscribeResult decodeSubscribe(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final SubscribeResponseDecoder decoder = new SubscribeResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final ErrorCode errorCode = decoder.errorCode();
    final long generation = decoder.generation();
    final List<Integer> assignedPartitions = new ArrayList<>();
    for (final SubscribeResponseDecoder.AssignedPartitionsDecoder ap :
        decoder.assignedPartitions()) {
      assignedPartitions.add(ap.partitionId());
    }
    final String errorMessage = decoder.errorMessage();
    return new SubscribeResult(errorCode, generation, assignedPartitions, errorMessage);
  }

  // -------------------------------------------------------------------------
  // Heartbeat

  /**
   * Encodes a {@code HeartbeatRequest} SBE message.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @return fully-framed byte array
   */
  public static byte[] encodeHeartbeat(final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final HeartbeatRequestEncoder encoder = new HeartbeatRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** Decoded result of a {@code HeartbeatResponse}. */
  public record HeartbeatResult(ErrorCode errorCode, long generation, String errorMessage) {}

  /**
   * Decodes a {@code HeartbeatResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static HeartbeatResult decodeHeartbeat(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final HeartbeatResponseDecoder decoder = new HeartbeatResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new HeartbeatResult(decoder.errorCode(), decoder.generation(), decoder.errorMessage());
  }

  // -------------------------------------------------------------------------
  // LatestPosition

  /**
   * Encodes a {@code LatestPositionRequest} SBE message.
   *
   * @param partitionId target partition
   * @return fully-framed byte array
   */
  public static byte[] encodeLatestPositionRequest(final int partitionId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(64);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final LatestPositionRequestEncoder encoder = new LatestPositionRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).partitionId(partitionId);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** Decoded result of a {@code LatestPositionResponse}. */
  public record LatestPositionResult(ErrorCode errorCode, long position, String errorMessage) {}

  /**
   * Decodes a {@code LatestPositionResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static LatestPositionResult decodeLatestPosition(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final LatestPositionResponseDecoder decoder = new LatestPositionResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new LatestPositionResult(
        decoder.errorCode(), decoder.position(), decoder.errorMessage());
  }

  // -------------------------------------------------------------------------
  // FetchAssignment

  /**
   * Encodes a {@code FetchAssignmentRequest} SBE message.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @return fully-framed byte array
   */
  public static byte[] encodeFetchAssignment(final String groupId, final String consumerId) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final FetchAssignmentRequestEncoder encoder = new FetchAssignmentRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).groupId(groupId).consumerId(consumerId);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /** Decoded result of a {@code FetchAssignmentResponse}. */
  public record FetchAssignmentResult(
      ErrorCode errorCode,
      long generation,
      List<Integer> assignedPartitions,
      String errorMessage) {}

  /**
   * Decodes a {@code FetchAssignmentResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static FetchAssignmentResult decodeFetchAssignment(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final FetchAssignmentResponseDecoder decoder = new FetchAssignmentResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    final ErrorCode errorCode = decoder.errorCode();
    final long generation = decoder.generation();
    final List<Integer> assignedPartitions = new ArrayList<>();
    for (final FetchAssignmentResponseDecoder.AssignedPartitionsDecoder ap :
        decoder.assignedPartitions()) {
      assignedPartitions.add(ap.partitionId());
    }
    return new FetchAssignmentResult(
        errorCode, generation, assignedPartitions, decoder.errorMessage());
  }

  // -------------------------------------------------------------------------
  // Helpers

  /** Copies the first {@code length} bytes from the buffer into a new array. */
  private static byte[] copyBytes(final ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }
}
