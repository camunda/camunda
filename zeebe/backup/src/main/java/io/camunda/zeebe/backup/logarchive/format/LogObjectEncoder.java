/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static io.camunda.zeebe.backup.logarchive.format.LogObjectFormat.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class LogObjectEncoder {

  private LogObjectEncoder() {}

  /**
   * Encodes the frames into one log object.
   *
   * @param term the writer's term; frames may carry lower terms
   * @throws IllegalArgumentException if frames are empty, indexes are not contiguous or the asqn of
   *     application frames is not strictly increasing
   */
  public static EncodedLogObject encode(
      final String timelineId,
      final int partitionId,
      final long term,
      final List<LogArchiveFrame> frames) {
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("frames must not be empty");
    }
    final byte[] timeline = timelineId.getBytes(StandardCharsets.UTF_8);
    if (timeline.length > 0xFFFF) {
      throw new IllegalArgumentException("timelineId too long");
    }

    long firstPosition = -1;
    long lastPosition = -1;
    long previousAsqn = -1;
    long frameBytes = 0;
    long expectedIndex = frames.getFirst().index();
    for (final var frame : frames) {
      if (frame.index() != expectedIndex) {
        throw new IllegalArgumentException(
            "indexes must be contiguous: expected %d but got %d"
                .formatted(expectedIndex, frame.index()));
      }
      expectedIndex++;
      if (frame.asqn() >= 0) {
        if (frame.asqn() <= previousAsqn) {
          throw new IllegalArgumentException(
              "asqn must increase: %d after %d at index %d"
                  .formatted(frame.asqn(), previousAsqn, frame.index()));
        }
        previousAsqn = frame.asqn();
      }
      if (frame.lowestPosition() >= 0) {
        if (firstPosition < 0) {
          firstPosition = frame.lowestPosition();
        }
        lastPosition = frame.highestPosition();
      }
      frameBytes += FRAME_FIXED_LENGTH + frame.serializedRecord().length;
    }

    final int headerLength = HEADER_FIXED_LENGTH + TIMELINE_LENGTH_FIELD + timeline.length;
    final long footerOffset = headerLength + frameBytes;
    final int footerEntries = (frames.size() + SPARSE_INTERVAL - 1) / SPARSE_INTERVAL;
    final long total =
        footerOffset + Integer.BYTES + (long) footerEntries * FOOTER_ENTRY_LENGTH + TRAILER_LENGTH;
    if (total > Integer.MAX_VALUE - 8) {
      throw new IllegalArgumentException("log object too large: " + total);
    }

    final var header =
        new LogObjectHeader(
            timelineId,
            partitionId,
            term,
            frames.getFirst().index(),
            frames.getLast().index(),
            firstPosition,
            lastPosition,
            frames.size(),
            headerLength);

    final ByteBuffer buf = ByteBuffer.allocate((int) total); // big-endian by default
    buf.putInt(HEADER_MAGIC)
        .putInt(headerLength)
        .putInt(partitionId)
        .putLong(term)
        .putLong(header.firstIndex())
        .putLong(header.lastIndex())
        .putLong(firstPosition)
        .putLong(lastPosition)
        .putInt(frames.size())
        .putShort((short) timeline.length)
        .put(timeline);

    final List<LogObjectFooter.Entry> sparse = new ArrayList<>(footerEntries);
    int i = 0;
    for (final var frame : frames) {
      if (i++ % SPARSE_INTERVAL == 0) {
        sparse.add(
            new LogObjectFooter.Entry(frame.index(), frame.lowestPosition(), buf.position()));
      }
      buf.putLong(frame.index())
          .putLong(frame.term())
          .putLong(frame.asqn())
          .putLong(frame.lowestPosition())
          .putLong(frame.highestPosition())
          .putLong(frame.checksum())
          .putInt(frame.serializedRecord().length)
          .put(frame.serializedRecord());
    }

    buf.putInt(sparse.size());
    for (final var e : sparse) {
      buf.putLong(e.index()).putLong(e.lowestPosition()).putLong(e.frameOffset());
    }
    buf.putLong(footerOffset).putInt(TRAILER_MAGIC);

    final byte[] bytes = buf.array();
    return new EncodedLogObject(bytes, header, sha256Hex(bytes));
  }

  private static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
