/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static io.camunda.zeebe.backup.logarchive.format.LogObjectFormat.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads log objects. Any structural problem is reported as {@link LogObjectFormatException}.
 * Checksums of the journal records are not verified here; {@code Journal.append} does that on
 * restore.
 */
public final class LogObjectDecoder {

  /** A byte range {@code [start, endExclusive)} of an object, for ranged reads. */
  public record ByteRange(long start, long endExclusive) {}

  private LogObjectDecoder() {}

  public static DecodedLogObject decode(final byte[] object) {
    try {
      final var header = readHeader(object);
      final var footer = readFooter(object);
      final ByteBuffer buf = ByteBuffer.wrap(object);
      buf.position(header.headerLength());
      final List<LogArchiveFrame> frames = new ArrayList<>(header.entryCount());
      for (int i = 0; i < header.entryCount(); i++) {
        if (buf.position() + FRAME_FIXED_LENGTH > footer.footerOffset()) {
          throw new LogObjectFormatException("frame %d exceeds the frame section".formatted(i));
        }
        final long index = buf.getLong();
        final long term = buf.getLong();
        final long asqn = buf.getLong();
        final long lowest = buf.getLong();
        final long highest = buf.getLong();
        final long checksum = buf.getLong();
        final int length = buf.getInt();
        if (length < 0 || buf.position() + (long) length > footer.footerOffset()) {
          throw new LogObjectFormatException("frame %d has invalid length %d".formatted(i, length));
        }
        final byte[] record = new byte[length];
        buf.get(record);
        frames.add(new LogArchiveFrame(index, term, asqn, lowest, highest, checksum, record));
      }
      if (buf.position() != footer.footerOffset()) {
        throw new LogObjectFormatException(
            "frames end at %d but footer starts at %d"
                .formatted(buf.position(), footer.footerOffset()));
      }
      if (!frames.isEmpty()
          && (frames.getFirst().index() != header.firstIndex()
              || frames.getLast().index() != header.lastIndex())) {
        throw new LogObjectFormatException("frame indexes do not match the header");
      }
      return new DecodedLogObject(header, frames, footer);
    } catch (final BufferUnderflowException | IndexOutOfBoundsException e) {
      throw new LogObjectFormatException("truncated log object", e);
    }
  }

  /** Reads the header from the first bytes of an object (at least the header length). */
  public static LogObjectHeader readHeader(final byte[] bytes) {
    try {
      final ByteBuffer buf = ByteBuffer.wrap(bytes);
      if (buf.getInt() != HEADER_MAGIC) {
        throw new LogObjectFormatException("invalid header magic");
      }
      final int headerLength = buf.getInt();
      final int partitionId = buf.getInt();
      final long term = buf.getLong();
      final long firstIndex = buf.getLong();
      final long lastIndex = buf.getLong();
      final long firstPosition = buf.getLong();
      final long lastPosition = buf.getLong();
      final int count = buf.getInt();
      final int timelineLength = Short.toUnsignedInt(buf.getShort());
      if (headerLength != HEADER_FIXED_LENGTH + TIMELINE_LENGTH_FIELD + timelineLength) {
        throw new LogObjectFormatException("inconsistent header length " + headerLength);
      }
      if (count < 0 || lastIndex - firstIndex + 1 != count) {
        throw new LogObjectFormatException("inconsistent entry count " + count);
      }
      final byte[] timeline = new byte[timelineLength];
      buf.get(timeline);
      return new LogObjectHeader(
          new String(timeline, StandardCharsets.UTF_8),
          partitionId,
          term,
          firstIndex,
          lastIndex,
          firstPosition,
          lastPosition,
          count,
          headerLength);
    } catch (final BufferUnderflowException e) {
      throw new LogObjectFormatException("truncated header", e);
    }
  }

  /** Reads the footer offset from the trailer at the end of {@code tail}. */
  public static long readFooterOffset(final byte[] tail) {
    if (tail.length < TRAILER_LENGTH) {
      throw new LogObjectFormatException("truncated trailer");
    }
    final ByteBuffer buf = ByteBuffer.wrap(tail, tail.length - TRAILER_LENGTH, TRAILER_LENGTH);
    final long footerOffset = buf.getLong();
    if (buf.getInt() != TRAILER_MAGIC) {
      throw new LogObjectFormatException("invalid trailer magic");
    }
    return footerOffset;
  }

  /** Reads the footer from a complete object. */
  public static LogObjectFooter readFooter(final byte[] object) {
    return readFooter(object, 0);
  }

  /**
   * Reads the footer from a suffix of an object, e.g. fetched with a ranged read of the tail.
   *
   * @param suffix the last bytes of the object, which must include the whole footer and trailer
   * @param suffixStartOffset the absolute offset in the object at which {@code suffix} begins
   */
  public static LogObjectFooter readFooter(final byte[] suffix, final long suffixStartOffset) {
    final long footerOffset = readFooterOffset(suffix);
    final long relative = footerOffset - suffixStartOffset;
    if (relative < 0 || relative + Integer.BYTES > suffix.length - TRAILER_LENGTH) {
      throw new LogObjectFormatException(
          "footer offset %d is outside the provided bytes".formatted(footerOffset));
    }
    final ByteBuffer buf = ByteBuffer.wrap(suffix);
    buf.position((int) relative);
    final int count = buf.getInt();
    final long available = suffix.length - TRAILER_LENGTH - buf.position();
    if (count < 0 || (long) count * FOOTER_ENTRY_LENGTH != available) {
      throw new LogObjectFormatException(
          "footer entry count %d does not match size".formatted(count));
    }
    final List<LogObjectFooter.Entry> entries = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      entries.add(new LogObjectFooter.Entry(buf.getLong(), buf.getLong(), buf.getLong()));
    }
    return new LogObjectFooter(List.copyOf(entries), footerOffset);
  }

  /**
   * Returns the offset of the last footer entry that is an application frame with lowestPosition
   * {@code <= position}. Entries of non-application frames (position -1) are never chosen, as their
   * position says nothing. If no entry qualifies, the first entry is returned, so scanning from
   * there finds the first frame at or after the position.
   */
  public static long findFrameOffset(final LogObjectFooter footer, final long position) {
    return footer.entries().get(coveringEntry(footer, position)).frameOffset();
  }

  /**
   * Byte range containing every application frame with positions in {@code [fromPosition,
   * toPosition]}: it starts at the covering footer entry of {@code fromPosition} and ends at the
   * first footer entry whose position is greater than {@code toPosition}, or the footer offset.
   * Frames in the range still have to be filtered by position by the reader.
   */
  public static ByteRange byteRange(
      final LogObjectFooter footer, final long fromPosition, final long toPosition) {
    final int start = coveringEntry(footer, fromPosition);
    long end = footer.footerOffset();
    for (int i = start + 1; i < footer.entries().size(); i++) {
      final var e = footer.entries().get(i);
      if (e.lowestPosition() > toPosition) {
        end = e.frameOffset();
        break;
      }
    }
    return new ByteRange(footer.entries().get(start).frameOffset(), end);
  }

  /** Byte range from the covering entry of {@code fromPosition} to the end of the frames. */
  public static ByteRange byteRange(final LogObjectFooter footer, final long fromPosition) {
    return byteRange(footer, fromPosition, Long.MAX_VALUE);
  }

  private static int coveringEntry(final LogObjectFooter footer, final long position) {
    if (footer.entries().isEmpty()) {
      throw new LogObjectFormatException("footer has no entries");
    }
    int result = 0;
    for (int i = 0; i < footer.entries().size(); i++) {
      final var p = footer.entries().get(i).lowestPosition();
      if (p >= 0) {
        if (p > position) {
          break;
        }
        result = i;
      }
    }
    return result;
  }
}
