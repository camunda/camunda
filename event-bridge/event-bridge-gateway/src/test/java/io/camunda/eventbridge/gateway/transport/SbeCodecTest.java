/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.core.protocol.AckRequestDecoder;
import io.camunda.eventbridge.core.protocol.AckResponseEncoder;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.protocol.HeartbeatRequestDecoder;
import io.camunda.eventbridge.core.protocol.HeartbeatResponseEncoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderEncoder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SbeCodec} — heartbeat and ack encode/decode paths introduced by the
 * consumer-group refactoring.
 *
 * <p>Each {@code encode*} test verifies the encoded bytes by round-tripping them through the raw
 * SBE decoder. Each {@code decode*} test builds a response with the raw SBE encoder and verifies
 * that {@link SbeCodec} deserialises all fields correctly.
 */
class SbeCodecTest {

  // -------------------------------------------------------------------------
  // Heartbeat request encoding

  @Nested
  class EncodeHeartbeatRequest {

    @Test
    void shouldEncodeGroupIdConsumerIdAndEpoch() {
      // given
      final String groupId = "group-1";
      final String consumerId = "consumer-1";
      final long epoch = 42L;

      // when
      final byte[] encoded = SbeCodec.encodeHeartbeat(groupId, consumerId, epoch, List.of());

      // then — round-trip decode with the raw SBE decoder
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final HeartbeatRequestDecoder decoder = new HeartbeatRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      assertThat(decoder.epoch()).isEqualTo(epoch);
      // SBE groups must be traversed in declaration order before accessing varData fields.
      decoder.ownedPartitions(); // advance past the empty owned-partitions group
      assertThat(decoder.groupId()).isEqualTo(groupId);
      assertThat(decoder.consumerId()).isEqualTo(consumerId);
    }

    @Test
    void shouldEncodeEmptyOwnedPartitions() {
      // when
      final byte[] encoded = SbeCodec.encodeHeartbeat("g", "c", 1L, List.of());

      // then
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final HeartbeatRequestDecoder decoder = new HeartbeatRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      assertThat(decoder.ownedPartitions().count()).isZero();
    }

    @Test
    void shouldEncodeMultipleOwnedPartitions() {
      // given
      final List<Integer> partitions = List.of(0, 1, 2, 7);

      // when
      final byte[] encoded = SbeCodec.encodeHeartbeat("g", "c", 5L, partitions);

      // then
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final HeartbeatRequestDecoder decoder = new HeartbeatRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      final List<Integer> decoded = new ArrayList<>();
      for (final HeartbeatRequestDecoder.OwnedPartitionsDecoder owned : decoder.ownedPartitions()) {
        decoded.add(owned.partitionId());
      }
      assertThat(decoded).containsExactlyElementsOf(partitions);
    }

    @Test
    void shouldEncodeFirstHeartbeatWithEpochZero() {
      // given — epoch 0 means "first heartbeat; consumer has never received a coordinator epoch"
      final byte[] encoded = SbeCodec.encodeHeartbeat("g", "c", 0L, List.of());

      // when
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final HeartbeatRequestDecoder decoder = new HeartbeatRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      // then
      assertThat(decoder.epoch()).isZero();
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat response decoding

  @Nested
  class DecodeHeartbeatResponse {

    @Test
    void shouldDecodeEpochAndEmptyDeltaLists() {
      // given
      final byte[] response =
          buildHeartbeatResponse(ErrorCode.NONE, 3L, List.of(), List.of(), List.of());

      // when
      final SbeCodec.HeartbeatResult result = SbeCodec.decodeHeartbeat(response);

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.epoch()).isEqualTo(3L);
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).isEmpty();
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldDecodeNonEmptyRevokeList() {
      // given
      final byte[] response =
          buildHeartbeatResponse(ErrorCode.NONE, 4L, List.of(1, 3), List.of(), List.of());

      // when
      final SbeCodec.HeartbeatResult result = SbeCodec.decodeHeartbeat(response);

      // then
      assertThat(result.epoch()).isEqualTo(4L);
      assertThat(result.revoke()).containsExactly(1, 3);
      assertThat(result.assign()).isEmpty();
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldDecodeNonEmptyAssignList() {
      // given
      final byte[] response =
          buildHeartbeatResponse(ErrorCode.NONE, 5L, List.of(), List.of(0, 2), List.of());

      // when
      final SbeCodec.HeartbeatResult result = SbeCodec.decodeHeartbeat(response);

      // then
      assertThat(result.epoch()).isEqualTo(5L);
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).containsExactly(0, 2);
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldDecodeNonEmptyFullAssignment() {
      // given — full reconcile: epoch advanced, fullAssignment populated, delta lists empty
      final byte[] response =
          buildHeartbeatResponse(ErrorCode.NONE, 6L, List.of(), List.of(), List.of(0, 1, 2, 3));

      // when
      final SbeCodec.HeartbeatResult result = SbeCodec.decodeHeartbeat(response);

      // then
      assertThat(result.epoch()).isEqualTo(6L);
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).isEmpty();
      assertThat(result.fullAssignment()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldDecodeErrorCodeAndMessage() {
      // given
      final byte[] response =
          buildHeartbeatResponseWithMessage(
              ErrorCode.CONSUMER_NOT_REGISTERED, 0L, "not registered");

      // when
      final SbeCodec.HeartbeatResult result = SbeCodec.decodeHeartbeat(response);

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
      assertThat(result.errorMessage()).isEqualTo("not registered");
    }
  }

  // -------------------------------------------------------------------------
  // Ack request encoding

  @Nested
  class EncodeAckRequest {

    @Test
    void shouldEncodeGroupIdConsumerIdAndEpoch() {
      // given
      final String groupId = "group-1";
      final String consumerId = "consumer-1";
      final long epoch = 7L;

      // when
      final byte[] encoded = SbeCodec.encodeAck(groupId, consumerId, epoch, List.of(), List.of());

      // then — round-trip decode
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final AckRequestDecoder decoder = new AckRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      assertThat(decoder.epoch()).isEqualTo(epoch);
      // SBE groups must be traversed in declaration order before accessing varData fields.
      decoder.revoked(); // advance past the empty revoked group
      decoder.assigned(); // advance past the empty assigned group
      assertThat(decoder.groupId()).isEqualTo(groupId);
      assertThat(decoder.consumerId()).isEqualTo(consumerId);
    }

    @Test
    void shouldEncodeEmptyRevokedAndAssignedLists() {
      // when
      final byte[] encoded = SbeCodec.encodeAck("g", "c", 1L, List.of(), List.of());

      // then
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final AckRequestDecoder decoder = new AckRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      assertThat(decoder.revoked().count()).isZero();
      assertThat(decoder.assigned().count()).isZero();
    }

    @Test
    void shouldEncodeRevokedPartitions() {
      // given
      final List<Integer> revoked = List.of(2, 4);

      // when
      final byte[] encoded = SbeCodec.encodeAck("g", "c", 2L, revoked, List.of());

      // then
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final AckRequestDecoder decoder = new AckRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      final List<Integer> decoded = new ArrayList<>();
      for (final AckRequestDecoder.RevokedDecoder r : decoder.revoked()) {
        decoded.add(r.partitionId());
      }
      assertThat(decoded).containsExactlyElementsOf(revoked);
    }

    @Test
    void shouldEncodeAssignedPartitions() {
      // given
      final List<Integer> assigned = List.of(0, 1, 3);

      // when
      final byte[] encoded = SbeCodec.encodeAck("g", "c", 3L, List.of(), assigned);

      // then — SBE groups must be accessed in declaration order (revoked before assigned)
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final AckRequestDecoder decoder = new AckRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      decoder.revoked(); // advance past the empty revoked group
      final List<Integer> decoded = new ArrayList<>();
      for (final AckRequestDecoder.AssignedDecoder a : decoder.assigned()) {
        decoded.add(a.partitionId());
      }
      assertThat(decoded).containsExactlyElementsOf(assigned);
    }

    @Test
    void shouldEncodeRevokedAndAssignedTogether() {
      // given
      final List<Integer> revoked = List.of(1);
      final List<Integer> assigned = List.of(0, 2);

      // when
      final byte[] encoded = SbeCodec.encodeAck("g", "c", 4L, revoked, assigned);

      // then
      final UnsafeBuffer buf = new UnsafeBuffer(encoded);
      final AckRequestDecoder decoder = new AckRequestDecoder();
      decoder.wrapAndApplyHeader(buf, 0, new MessageHeaderDecoder());

      final List<Integer> decodedRevoked = new ArrayList<>();
      for (final AckRequestDecoder.RevokedDecoder r : decoder.revoked()) {
        decodedRevoked.add(r.partitionId());
      }
      final List<Integer> decodedAssigned = new ArrayList<>();
      for (final AckRequestDecoder.AssignedDecoder a : decoder.assigned()) {
        decodedAssigned.add(a.partitionId());
      }

      assertThat(decodedRevoked).containsExactlyElementsOf(revoked);
      assertThat(decodedAssigned).containsExactlyElementsOf(assigned);
    }
  }

  // -------------------------------------------------------------------------
  // Ack response decoding

  @Nested
  class DecodeAckResponse {

    @Test
    void shouldDecodeNoneErrorCode() {
      // given
      final byte[] response = buildAckResponse(ErrorCode.NONE, "");

      // when
      final SbeCodec.AckResult result = SbeCodec.decodeAckResponse(response);

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.NONE);
      assertThat(result.errorMessage()).isEmpty();
    }

    @Test
    void shouldDecodeConsumerNotRegisteredErrorCode() {
      // given
      final byte[] response =
          buildAckResponse(ErrorCode.CONSUMER_NOT_REGISTERED, "consumer unknown");

      // when
      final SbeCodec.AckResult result = SbeCodec.decodeAckResponse(response);

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.CONSUMER_NOT_REGISTERED);
      assertThat(result.errorMessage()).isEqualTo("consumer unknown");
    }

    @Test
    void shouldDecodeCoordinatorUnavailableErrorCode() {
      // given
      final byte[] response = buildAckResponse(ErrorCode.COORDINATOR_UNAVAILABLE, "unavailable");

      // when
      final SbeCodec.AckResult result = SbeCodec.decodeAckResponse(response);

      // then
      assertThat(result.errorCode()).isEqualTo(ErrorCode.COORDINATOR_UNAVAILABLE);
      assertThat(result.errorMessage()).isEqualTo("unavailable");
    }
  }

  // -------------------------------------------------------------------------
  // SBE response builders

  private static byte[] buildHeartbeatResponse(
      final ErrorCode errorCode,
      final long epoch,
      final List<Integer> revoke,
      final List<Integer> assign,
      final List<Integer> fullAssignment) {
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final HeartbeatResponseEncoder encoder = new HeartbeatResponseEncoder();
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).epoch(epoch);

    final HeartbeatResponseEncoder.RevokeEncoder revokeEncoder = encoder.revokeCount(revoke.size());
    for (final int p : revoke) {
      revokeEncoder.next().partitionId(p);
    }
    final HeartbeatResponseEncoder.AssignEncoder assignEncoder = encoder.assignCount(assign.size());
    for (final int p : assign) {
      assignEncoder.next().partitionId(p);
    }
    final HeartbeatResponseEncoder.FullAssignmentEncoder faEncoder =
        encoder.fullAssignmentCount(fullAssignment.size());
    for (final int p : fullAssignment) {
      faEncoder.next().partitionId(p);
    }
    encoder.errorMessage("");

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] buildHeartbeatResponseWithMessage(
      final ErrorCode errorCode, final long epoch, final String message) {
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final HeartbeatResponseEncoder encoder = new HeartbeatResponseEncoder();
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(256);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).epoch(epoch);
    encoder.revokeCount(0);
    encoder.assignCount(0);
    encoder.fullAssignmentCount(0);
    encoder.errorMessage(message);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] buildAckResponse(final ErrorCode errorCode, final String message) {
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final AckResponseEncoder encoder = new AckResponseEncoder();
    final ExpandableArrayBuffer buf = new ExpandableArrayBuffer(64);

    encoder.wrapAndApplyHeader(buf, 0, headerEncoder).errorCode(errorCode).errorMessage(message);

    return copyBytes(buf, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private static byte[] copyBytes(final ExpandableArrayBuffer buf, final int length) {
    final byte[] result = new byte[length];
    buf.getBytes(0, result, 0, length);
    return result;
  }
}
