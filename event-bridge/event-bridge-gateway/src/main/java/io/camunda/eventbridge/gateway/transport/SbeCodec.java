/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import io.camunda.eventbridge.protocol.AckRequestEncoder;
import io.camunda.eventbridge.protocol.AckResponseDecoder;
import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.protocol.PollRequestEncoder;
import io.camunda.eventbridge.protocol.PollResponseDecoder;
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
        .epoch(params.generation())
        .groupId(params.groupId())
        .consumerId(params.consumerId());

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  // -------------------------------------------------------------------------
  // PollRequest / PollResponse

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
    final long generation = decoder.epoch();

    // Groups must be decoded in declaration order: events first, then assignedPartitions.
    final List<PollEvent> events = new ArrayList<>();
    for (final PollResponseDecoder.EventsDecoder evt : decoder.events()) {
      final int len = evt.payloadLength();
      final byte[] payload = new byte[len];
      evt.getPayload(payload, 0, len);
      events.add(new PollEvent(evt.position(), payload));
    }

    final String errorMessage = decoder.errorMessage();
    return new PollResult(errorCode, nextPosition, generation, events, errorMessage);
  }

  // -------------------------------------------------------------------------
  // CommitOffset

  /**
   * Encodes an {@code AckRequest} SBE message.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @param epoch the epoch value from the coordinator at the time the assignment was issued
   * @param revoked partition IDs the consumer has stopped processing
   * @param assigned partition IDs the consumer has started processing
   * @return fully-framed byte array
   */
  public static byte[] encodeAck(
      final String groupId,
      final String consumerId,
      final long epoch,
      final List<Integer> revoked,
      final List<Integer> assigned) {
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(INITIAL_BUFFER_SIZE);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final AckRequestEncoder encoder = new AckRequestEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).epoch(epoch);

    final AckRequestEncoder.RevokedEncoder revokedEncoder = encoder.revokedCount(revoked.size());
    for (final int partitionId : revoked) {
      revokedEncoder.next().partitionId(partitionId);
    }

    final AckRequestEncoder.AssignedEncoder assignedEncoder =
        encoder.assignedCount(assigned.size());
    for (final int partitionId : assigned) {
      assignedEncoder.next().partitionId(partitionId);
    }

    encoder.groupId(groupId).consumerId(consumerId);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  /**
   * Decodes an {@code AckResponse} SBE message.
   *
   * @param bytes the raw response bytes
   * @return decoded result
   */
  public static AckResult decodeAckResponse(final byte[] bytes) {
    final UnsafeBuffer buf = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final AckResponseDecoder decoder = new AckResponseDecoder();
    decoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    return new AckResult(decoder.errorCode(), decoder.errorMessage());
  }

  // -------------------------------------------------------------------------
  // Ack

  /** Copies the first {@code length} bytes from the buffer into a new array. */
  private static byte[] copyBytes(final ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }

  /** Decoded result of a {@code PublishBatchResponse}. */
  public record PublishBatchResult(
      ErrorCode errorCode, List<Long> positions, String errorMessage) {}

  /** Poll request parameters. */
  public record PollParams(
      int partitionId,
      String groupId,
      String consumerId,
      long fromPosition,
      int maxRecords,
      int serverWaitMs,
      long generation) {}

  // -------------------------------------------------------------------------
  // LatestPosition

  /** A single event returned in a poll response. */
  public record PollEvent(long position, byte[] payload) {}

  /** Decoded result of a {@code PollResponse}. */
  public record PollResult(
      ErrorCode errorCode,
      long nextPosition,
      long generation,
      List<PollEvent> events,
      String errorMessage) {}

  /** Decoded result of a {@code CommitOffsetResponse}. */
  public record CommitResult(ErrorCode errorCode, String errorMessage) {}

  // -------------------------------------------------------------------------
  // FetchAssignment

  /** Decoded result of a {@code HeartbeatResponse}. */
  public record HeartbeatResult(
      ErrorCode errorCode,
      long epoch,
      List<Integer> revoke,
      List<Integer> assign,
      List<Integer> fullAssignment,
      String errorMessage) {}

  /** Decoded result of an {@code AckResponse}. */
  public record AckResult(ErrorCode errorCode, String errorMessage) {}

  /** Decoded result of a {@code LatestPositionResponse}. */
  public record LatestPositionResult(ErrorCode errorCode, long position, String errorMessage) {}
}
