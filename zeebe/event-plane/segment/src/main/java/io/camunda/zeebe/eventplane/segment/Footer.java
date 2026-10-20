/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidFooter;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.Truncated;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * What ends a segment, in format version 1. Without records, both positions and the timestamp are
 * {@link Entry#NO_POSITION}.
 */
public record Footer(
    UUID history,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    long latestRecordTimestamp,
    long bodyLength,
    Level level) {

  /** How much of an object's end a reader first reads: it usually holds all of the metadata too. */
  public static final int USUAL_TAIL_LENGTH = 16 * 1024;

  static final int VERSION = 1;
  static final int LENGTH = 66;

  public Footer {
    if (firstIndex < 1 || lastIndex < firstIndex) {
      throw new InvalidFooter.Indexes(firstIndex, lastIndex);
    }
    final boolean records = firstPosition != NO_POSITION || lastPosition != NO_POSITION;
    if (records && (firstPosition < 1 || lastPosition < firstPosition)) {
      throw new InvalidFooter.Positions(firstPosition, lastPosition);
    }
    if (!validTimestamp(records, latestRecordTimestamp)) {
      throw new InvalidFooter.Timestamp(records, latestRecordTimestamp);
    }
    if (bodyLength < 1) {
      throw new InvalidFooter.BodyLength(bodyLength);
    }
  }

  public boolean hasRecords() {
    return firstPosition != NO_POSITION;
  }

  byte[] encode() {
    final var footer = ByteBuffer.allocate(LENGTH);
    // A UUID in RFC 9562's byte order, big-endian as its string shows it, so that readers in any
    // language agree on it. Two little-endian longs would reverse each half.
    footer.putLong(history.getMostSignificantBits()).putLong(history.getLeastSignificantBits());
    footer
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(firstIndex)
        .putLong(lastIndex)
        .putLong(firstPosition)
        .putLong(lastPosition)
        .putLong(latestRecordTimestamp)
        .putLong(bodyLength)
        .put((byte) level.code())
        .put((byte) VERSION);
    return footer.array();
  }

  /**
   * Reads the footer from the end of {@code tail}, which ends where the object ends: from its
   * remaining bytes, without moving its position.
   *
   * @throws InvalidSegmentException if the tail is shorter than the footer, or the footer invalid
   */
  public static Footer read(ByteBuffer tail) {
    if (!tail.hasRemaining()) {
      throw truncated(tail);
    }
    final int version = Byte.toUnsignedInt(tail.get(tail.limit() - 1));
    if (version != VERSION) {
      throw new InvalidFooter.UnknownVersion(version);
    }
    if (tail.remaining() < LENGTH) {
      throw truncated(tail);
    }
    final var footer = tail.slice(tail.limit() - LENGTH, LENGTH);
    final var history = new UUID(footer.getLong(0), footer.getLong(8));
    footer.order(ByteOrder.LITTLE_ENDIAN);
    return new Footer(
        history,
        footer.getLong(16),
        footer.getLong(24),
        footer.getLong(32),
        footer.getLong(40),
        footer.getLong(48),
        footer.getLong(56),
        Level.of(Byte.toUnsignedInt(footer.get(64))));
  }

  /** Whether a segment's latest record timestamp is at least 0 with records, and -1 without. */
  static boolean validTimestamp(boolean hasRecords, long latestRecordTimestamp) {
    return hasRecords ? latestRecordTimestamp >= 0 : latestRecordTimestamp == NO_POSITION;
  }

  static String timestampExpectation(boolean hasRecords, long latestRecordTimestamp) {
    return hasRecords
        ? "Expected a latest record timestamp of at least 0 for a segment with records, but got "
            + latestRecordTimestamp
        : "Expected latest record timestamp %d for a segment without records, but got %d"
            .formatted(NO_POSITION, latestRecordTimestamp);
  }

  private static Truncated truncated(ByteBuffer tail) {
    return new Truncated(
        "Expected a footer of %d bytes at the end of the tail, but the tail has %d bytes"
            .formatted(LENGTH, tail.remaining()));
  }
}
