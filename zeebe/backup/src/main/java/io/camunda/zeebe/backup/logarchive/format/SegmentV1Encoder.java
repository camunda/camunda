/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static io.camunda.zeebe.backup.logarchive.format.SegmentV1.*;

import com.github.luben.zstd.ZstdCompressCtx;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.record.SBESerializer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Encodes journal entries into the event plane's segment format v1: one zstd frame holding all
 * entries as the journal stores them, then the metadata (identity, frame index, directory), then
 * the 66-byte footer. All numbers are little-endian. The same entries give the same bytes.
 */
public final class SegmentV1Encoder {

  private SegmentV1Encoder() {}

  /**
   * @param timelineId the timeline the frames belong to; only its name-based UUID is written
   * @param frames the entries; indexes must be contiguous and asqns strictly increasing
   * @param latestTimestamp the highest record timestamp of the application entries, -1 without
   *     records
   * @throws IllegalArgumentException if frames are empty, indexes are not contiguous or the asqn of
   *     application frames is not strictly increasing
   */
  public static EncodedSegment encode(
      final SegmentIdentity identity,
      final String timelineId,
      final List<LogArchiveFrame> frames,
      final long latestTimestamp) {
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("frames must not be empty");
    }
    final byte[] clusterId = identity.clusterId().getBytes(StandardCharsets.UTF_8);
    final byte[] tenant = identity.physicalTenantId().getBytes(StandardCharsets.UTF_8);
    if (clusterId.length > 0xFFFF || tenant.length > 0xFFFF) {
      throw new IllegalArgumentException("identity too long");
    }

    long firstPosition = -1;
    long lastPosition = -1;
    long previousAsqn = -1;
    long bodyBytes = 0;
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
      bodyBytes += 1 + entryMetadataLength() + frame.serializedRecord().length;
    }
    if (bodyBytes > Integer.MAX_VALUE - 8) {
      throw new IllegalArgumentException("segment too large: " + bodyBytes);
    }

    final byte[] entries = entries(frames, (int) bodyBytes);
    final byte[] body = compress(entries);

    final int identityLength = 2 + clusterId.length + 2 + tenant.length + 4;
    final int metadataLength =
        identityLength + FRAME_INDEX_ENTRY_LENGTH + 2 * DIRECTORY_ENTRY_LENGTH + Integer.BYTES;
    final byte[] bytes = new byte[body.length + metadataLength + FOOTER_LENGTH];
    final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(body);

    final int metadataStart = buf.position();
    buf.putShort((short) clusterId.length)
        .put(clusterId)
        .putShort((short) tenant.length)
        .put(tenant)
        .putInt(identity.partitionId());
    final int frameIndexOffset = buf.position() - metadataStart;
    buf.putLong(firstPosition).putLong(0);
    // the directory: key, offset from the start of the metadata, length; then the count
    buf.putShort((short) KEY_IDENTITY).putInt(0).putInt(identityLength);
    buf.putShort((short) KEY_FRAME_INDEX).putInt(frameIndexOffset).putInt(FRAME_INDEX_ENTRY_LENGTH);
    buf.putInt(2);

    final var footer =
        new SegmentFooter(
            history(timelineId),
            frames.getFirst().index(),
            frames.getLast().index(),
            firstPosition,
            lastPosition,
            latestTimestamp,
            body.length,
            LEVEL_PUBLISHER);
    final var history = footer.history();
    // the UUID's 16 bytes in RFC 4122 order: most significant byte first
    buf.order(ByteOrder.BIG_ENDIAN)
        .putLong(history.getMostSignificantBits())
        .putLong(history.getLeastSignificantBits())
        .order(ByteOrder.LITTLE_ENDIAN);
    buf.putLong(footer.firstIndex())
        .putLong(footer.lastIndex())
        .putLong(footer.firstPosition())
        .putLong(footer.lastPosition())
        .putLong(footer.latestTimestamp())
        .putLong(footer.bodyLength())
        .put((byte) footer.level())
        .put((byte) VERSION);
    return new EncodedSegment(bytes, footer, sha256Hex(bytes));
  }

  private static int entryMetadataLength() {
    return new SBESerializer().getMetadataLength();
  }

  private static byte[] entries(final List<LogArchiveFrame> frames, final int length) {
    final var serializer = new SBESerializer();
    final byte[] entries = new byte[length];
    final var buffer = new UnsafeBuffer(entries);
    int offset = 0;
    for (final var frame : frames) {
      final byte[] record = frame.serializedRecord();
      buffer.putByte(offset, JOURNAL_FRAME_VERSION);
      offset += 1;
      offset +=
          serializer.writeMetadata(
              new RecordMetadata(frame.checksum(), record.length), buffer, offset);
      buffer.putBytes(offset, record);
      offset += record.length;
    }
    return entries;
  }

  private static byte[] compress(final byte[] entries) {
    try (final var context = new ZstdCompressCtx()) {
      context
          .setLevel(ZSTD_LEVEL)
          .setWindowLog(ZSTD_WINDOW_LOG)
          .setContentSize(true)
          .setChecksum(false)
          .setDictID(false);
      return context.compress(entries);
    }
  }

  static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
