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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;

/**
 * Encodes journal entries into the event plane's segment format v1: zstd frames holding the entries
 * as the journal stores them, then the metadata (identity, frame index, directory), then the
 * 66-byte footer. All numbers are little-endian. The same entries give the same bytes.
 *
 * <p>An object cut by the archiver is one frame ({@link #encode}). A merged object is written frame
 * by frame through a {@link Writer} (spec section 20.2): a new frame starts once a frame holds a
 * given number of entry bytes, so that a large object never has to be held in memory.
 */
public final class SegmentV1Encoder {

  private SegmentV1Encoder() {}

  /**
   * Encodes all frames into one zstd frame.
   *
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
    final var bytes = new ByteArrayOutputStream();
    final var writer = new Writer(bytes, identity, timelineId, Long.MAX_VALUE);
    frames.forEach(writer::append);
    final var written = writer.finish(latestTimestamp);
    return new EncodedSegment(bytes.toByteArray(), written.footer(), written.sha256());
  }

  /**
   * Starts a segment that is written frame by frame to {@code out}.
   *
   * @param timelineId the timeline the frames belong to; only its name-based UUID is written
   * @param frameBytes a new zstd frame starts once the current one holds at least this many bytes
   *     of entries; a frame therefore exceeds it by less than one entry
   */
  public static Writer writer(
      final OutputStream out,
      final SegmentIdentity identity,
      final String timelineId,
      final long frameBytes) {
    return new Writer(out, identity, timelineId, frameBytes);
  }

  static String sha256Hex(final byte[] bytes) {
    return HexFormat.of().formatHex(sha256().digest(bytes));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * What a {@link Writer} wrote.
   *
   * @param footer what the footer of the object says
   * @param sizeBytes the length of the whole object
   * @param sha256 lowercase hex SHA-256 of the whole object
   * @param frameCount the number of zstd frames in the body
   */
  public record WrittenSegment(
      SegmentFooter footer, long sizeBytes, String sha256, int frameCount) {}

  /**
   * Writes one segment to an output stream, one zstd frame at a time, and computes its size and
   * SHA-256 while writing. Not thread-safe. The stream is not closed.
   */
  public static final class Writer {

    // what a single compression call takes here
    private static final long MAX_FRAME_ENTRY_BYTES = Integer.MAX_VALUE - 8L;

    private final OutputStream out;
    private final MessageDigest digest = sha256();
    private final SegmentIdentity identity;
    private final String timelineId;
    private final long frameBytes;
    private final SBESerializer serializer = new SBESerializer();
    private final int entryMetadataLength = serializer.getMetadataLength();
    private final ExpandableArrayBuffer frame = new ExpandableArrayBuffer();
    // per written frame: its first position (-1 if none) and its offset in the body
    private final List<long[]> frameIndex = new ArrayList<>();
    private int frameLength;
    private long frameFirstPosition = -1;
    private long written;
    private long entryCount;
    private long firstIndex = -1;
    private long expectedIndex = -1;
    private long previousAsqn = -1;
    private long firstPosition = -1;
    private long lastPosition = -1;
    private boolean finished;

    private Writer(
        final OutputStream out,
        final SegmentIdentity identity,
        final String timelineId,
        final long frameBytes) {
      if (frameBytes <= 0) {
        throw new IllegalArgumentException("frameBytes must be positive, but was " + frameBytes);
      }
      if (identity.clusterId().getBytes(StandardCharsets.UTF_8).length > 0xFFFF
          || identity.physicalTenantId().getBytes(StandardCharsets.UTF_8).length > 0xFFFF) {
        throw new IllegalArgumentException("identity too long");
      }
      this.out = out;
      this.identity = identity;
      this.timelineId = timelineId;
      this.frameBytes = frameBytes;
    }

    /**
     * Appends the next entry.
     *
     * @throws IllegalArgumentException if the index does not follow the previous one, or the asqn
     *     of an application entry does not increase
     * @throws UncheckedIOException if a completed frame cannot be written
     */
    public void append(final LogArchiveFrame entry) {
      if (finished) {
        throw new IllegalStateException("segment already finished");
      }
      if (expectedIndex >= 0 && entry.index() != expectedIndex) {
        throw new IllegalArgumentException(
            "indexes must be contiguous: expected %d but got %d"
                .formatted(expectedIndex, entry.index()));
      }
      if (entry.asqn() >= 0) {
        if (entry.asqn() <= previousAsqn) {
          throw new IllegalArgumentException(
              "asqn must increase: %d after %d at index %d"
                  .formatted(entry.asqn(), previousAsqn, entry.index()));
        }
        previousAsqn = entry.asqn();
      }
      final byte[] record = entry.serializedRecord();
      final long length = 1L + entryMetadataLength + record.length;
      if (frameLength + length > MAX_FRAME_ENTRY_BYTES) {
        throw new IllegalArgumentException("segment too large: " + (frameLength + length));
      }
      if (firstIndex < 0) {
        firstIndex = entry.index();
      }
      expectedIndex = entry.index() + 1;
      entryCount++;
      if (entry.lowestPosition() >= 0) {
        if (firstPosition < 0) {
          firstPosition = entry.lowestPosition();
        }
        if (frameFirstPosition < 0) {
          frameFirstPosition = entry.lowestPosition();
        }
        lastPosition = entry.highestPosition();
      }

      frame.checkLimit(frameLength + (int) length);
      frame.putByte(frameLength, JOURNAL_FRAME_VERSION);
      int offset = frameLength + 1;
      offset +=
          serializer.writeMetadata(
              new RecordMetadata(entry.checksum(), record.length), frame, offset);
      frame.putBytes(offset, record);
      frameLength = offset + record.length;

      if (frameLength >= frameBytes) {
        flushFrame();
      }
    }

    /**
     * Writes the last frame, the metadata and the footer.
     *
     * @param latestTimestamp the highest record timestamp of the application entries, -1 without
     *     records
     * @throws IllegalArgumentException if no entry was appended
     * @throws UncheckedIOException if the stream fails
     */
    public WrittenSegment finish(final long latestTimestamp) {
      if (finished) {
        throw new IllegalStateException("segment already finished");
      }
      if (entryCount == 0) {
        throw new IllegalArgumentException("frames must not be empty");
      }
      if (frameLength > 0) {
        flushFrame();
      }
      finished = true;
      final long bodyLength = written;

      final byte[] clusterId = identity.clusterId().getBytes(StandardCharsets.UTF_8);
      final byte[] tenant = identity.physicalTenantId().getBytes(StandardCharsets.UTF_8);
      final int identityLength = 2 + clusterId.length + 2 + tenant.length + 4;
      final int frameIndexLength = frameIndex.size() * FRAME_INDEX_ENTRY_LENGTH;
      final int metadataLength =
          identityLength + frameIndexLength + 2 * DIRECTORY_ENTRY_LENGTH + Integer.BYTES;
      final ByteBuffer buf =
          ByteBuffer.allocate(metadataLength + FOOTER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
      buf.putShort((short) clusterId.length)
          .put(clusterId)
          .putShort((short) tenant.length)
          .put(tenant)
          .putInt(identity.partitionId());
      final int frameIndexOffset = buf.position();
      for (final long[] entry : frameIndex) {
        buf.putLong(entry[0]).putLong(entry[1]);
      }
      // the directory: key, offset from the start of the metadata, length; then the count
      buf.putShort((short) KEY_IDENTITY).putInt(0).putInt(identityLength);
      buf.putShort((short) KEY_FRAME_INDEX).putInt(frameIndexOffset).putInt(frameIndexLength);
      buf.putInt(2);

      final var footer =
          new SegmentFooter(
              history(timelineId),
              firstIndex,
              expectedIndex - 1,
              firstPosition,
              lastPosition,
              latestTimestamp,
              bodyLength,
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
      write(buf.array());
      return new WrittenSegment(
          footer, written, HexFormat.of().formatHex(digest.digest()), frameIndex.size());
    }

    private void flushFrame() {
      frameIndex.add(new long[] {frameFirstPosition, written});
      write(compress(Arrays.copyOf(frame.byteArray(), frameLength)));
      frameLength = 0;
      frameFirstPosition = -1;
    }

    private void write(final byte[] bytes) {
      try {
        out.write(bytes);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      digest.update(bytes);
      written += bytes.length;
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
  }
}
