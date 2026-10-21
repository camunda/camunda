/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static io.camunda.zeebe.backup.logarchive.format.SegmentV1.*;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import io.camunda.zeebe.journal.record.SBESerializer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes the event plane's segment format v1, as the cluster writes it: the zstd frames listed by
 * the frame index (one for a cut object, several for a merged one), the metadata and the footer.
 * Anything that does not fit the format fails with a {@link LogObjectFormatException}; the entries
 * carry the journal's checksums, which the journal verifies when restore appends them.
 */
public final class SegmentV1Decoder {

  /** Upper bound for a decompressed frame, so that a corrupt content size cannot exhaust memory. */
  private static final long MAX_BODY_BYTES = Integer.MAX_VALUE - 8L;

  private SegmentV1Decoder() {}

  /** Reads the footer from the last {@value SegmentV1#FOOTER_LENGTH} or more bytes of an object. */
  public static SegmentFooter readFooter(final byte[] tail) {
    if (tail.length < FOOTER_LENGTH) {
      throw new LogObjectFormatException("truncated footer: %d bytes".formatted(tail.length));
    }
    if (tail[tail.length - 1] != VERSION) {
      throw new LogObjectFormatException(
          "unsupported footer version %d".formatted(tail[tail.length - 1]));
    }
    final var buf = ByteBuffer.wrap(tail, tail.length - FOOTER_LENGTH, FOOTER_LENGTH);
    final long most = buf.order(ByteOrder.BIG_ENDIAN).getLong();
    final long least = buf.getLong();
    buf.order(ByteOrder.LITTLE_ENDIAN);
    final var footer =
        new SegmentFooter(
            new UUID(most, least),
            buf.getLong(),
            buf.getLong(),
            buf.getLong(),
            buf.getLong(),
            buf.getLong(),
            buf.getLong(),
            buf.get());
    if (footer.firstIndex() < 0
        || footer.lastIndex() < footer.firstIndex()
        || footer.lastIndex() - footer.firstIndex() >= Integer.MAX_VALUE) {
      throw new LogObjectFormatException(
          "invalid index range %d to %d".formatted(footer.firstIndex(), footer.lastIndex()));
    }
    if (footer.bodyLength() < 0) {
      throw new LogObjectFormatException("invalid body length " + footer.bodyLength());
    }
    return footer;
  }

  public static DecodedSegment decode(final byte[] object) {
    try {
      return doDecode(object);
    } catch (final BufferUnderflowException | IndexOutOfBoundsException e) {
      throw new LogObjectFormatException("truncated segment", e);
    }
  }

  private static DecodedSegment doDecode(final byte[] object) {
    final var footer = readFooter(object);
    final long metadataEnd = object.length - FOOTER_LENGTH;
    if (footer.bodyLength() > metadataEnd) {
      throw new LogObjectFormatException(
          "body length %d exceeds the object, which has %d bytes before the footer"
              .formatted(footer.bodyLength(), metadataEnd));
    }
    final int metadataStart = (int) footer.bodyLength();
    final int metadataLength = (int) metadataEnd - metadataStart;

    final var metadata = ByteBuffer.wrap(object, metadataStart, metadataLength).slice();
    metadata.order(ByteOrder.LITTLE_ENDIAN);
    final var directory = readDirectory(metadata, metadataLength);
    final var identity = readIdentity(metadata, directory.get(KEY_IDENTITY));
    final var frameIndex = readFrameIndex(metadata, directory.get(KEY_FRAME_INDEX));

    final var frames = decodeEntries(object, footer, frameIndex);
    return new DecodedSegment(
        footer,
        identity,
        frameIndex.stream().map(e -> e[0]).toList(),
        frameIndex.stream().map(e -> e[1]).toList(),
        frames);
  }

  private static java.util.Map<Integer, int[]> readDirectory(
      final ByteBuffer metadata, final int metadataLength) {
    if (metadataLength < Integer.BYTES) {
      throw new LogObjectFormatException("truncated metadata");
    }
    final int count = metadata.getInt(metadataLength - Integer.BYTES);
    final long directoryLength = (long) count * DIRECTORY_ENTRY_LENGTH + Integer.BYTES;
    if (count < 0 || directoryLength > metadataLength) {
      throw new LogObjectFormatException("invalid metadata directory size " + count);
    }
    final var entries = new java.util.HashMap<Integer, int[]>();
    final long dataLength = metadataLength - directoryLength;
    int position = (int) dataLength;
    for (int i = 0; i < count; i++) {
      final int key = Short.toUnsignedInt(metadata.getShort(position));
      final long offset = Integer.toUnsignedLong(metadata.getInt(position + 2));
      final long length = Integer.toUnsignedLong(metadata.getInt(position + 6));
      position += DIRECTORY_ENTRY_LENGTH;
      if (offset + length > dataLength) {
        throw new LogObjectFormatException(
            "metadata entry %d at %d with length %d is outside the metadata"
                .formatted(key, offset, length));
      }
      if (entries.put(key, new int[] {(int) offset, (int) length}) != null) {
        throw new LogObjectFormatException("duplicate metadata entry " + key);
      }
    }
    for (final int required : new int[] {KEY_IDENTITY, KEY_FRAME_INDEX}) {
      if (!entries.containsKey(required)) {
        throw new LogObjectFormatException("missing metadata entry " + required);
      }
    }
    return entries;
  }

  private static SegmentIdentity readIdentity(final ByteBuffer metadata, final int[] entry) {
    final var buf = metadata.slice(entry[0], entry[1]).order(ByteOrder.LITTLE_ENDIAN);
    try {
      final String clusterId = readString(buf);
      final String tenant = readString(buf);
      final int partitionId = buf.getInt();
      if (buf.hasRemaining()) {
        throw new LogObjectFormatException("identity has trailing bytes");
      }
      return new SegmentIdentity(clusterId, tenant, partitionId);
    } catch (final BufferUnderflowException | IllegalArgumentException e) {
      throw new LogObjectFormatException("invalid identity", e);
    }
  }

  private static String readString(final ByteBuffer buf) {
    final byte[] bytes = new byte[Short.toUnsignedInt(buf.getShort())];
    buf.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static List<long[]> readFrameIndex(final ByteBuffer metadata, final int[] entry) {
    if (entry[1] == 0 || entry[1] % FRAME_INDEX_ENTRY_LENGTH != 0) {
      throw new LogObjectFormatException("invalid frame index length " + entry[1]);
    }
    final var buf = metadata.slice(entry[0], entry[1]).order(ByteOrder.LITTLE_ENDIAN);
    final var frames = new ArrayList<long[]>();
    while (buf.hasRemaining()) {
      frames.add(new long[] {buf.getLong(), buf.getLong()});
    }
    return frames;
  }

  /**
   * Decompresses every zstd frame of the body, as listed by the frame index, and reads the entries
   * of each. An entry never spans two frames.
   */
  private static List<LogArchiveFrame> decodeEntries(
      final byte[] object, final SegmentFooter footer, final List<long[]> frameIndex) {
    final int bodyLength = (int) footer.bodyLength();
    final var frames = new ArrayList<LogArchiveFrame>(footer.entryCount());
    final var serializer = new SBESerializer();
    long previousAsqn = -1;
    long firstPosition = -1;
    for (int i = 0; i < frameIndex.size(); i++) {
      final long start = frameIndex.get(i)[1];
      final long end = i + 1 < frameIndex.size() ? frameIndex.get(i + 1)[1] : bodyLength;
      if ((i == 0 && start != 0) || start < 0 || start >= end || end > bodyLength) {
        throw new LogObjectFormatException(
            "invalid frame %d at body offset %d, next frame or body end at %d"
                .formatted(i, start, end));
      }
      final int before = frames.size();
      final long frameFirstAsqn =
          decodeFrame(
              decompress(object, (int) start, (int) (end - start)),
              serializer,
              previousAsqn,
              frames);
      if (frames.size() == before) {
        throw new LogObjectFormatException("frame %d holds no entries".formatted(i));
      }
      if (frameFirstAsqn != frameIndex.get(i)[0]) {
        throw new LogObjectFormatException(
            "frame %d starts at position %d, but the frame index says %d"
                .formatted(i, frameFirstAsqn, frameIndex.get(i)[0]));
      }
      for (int f = before; f < frames.size(); f++) {
        final long asqn = frames.get(f).asqn();
        if (asqn >= 0) {
          previousAsqn = asqn;
          if (firstPosition < 0) {
            firstPosition = asqn;
          }
        }
      }
    }

    if (frames.size() != footer.entryCount()) {
      throw new LogObjectFormatException(
          "footer says %d entries but the body has %d"
              .formatted(footer.entryCount(), frames.size()));
    }
    for (int i = 0; i < frames.size(); i++) {
      if (frames.get(i).index() != footer.firstIndex() + i) {
        throw new LogObjectFormatException(
            "entry %d has index %d, expected %d"
                .formatted(i, frames.get(i).index(), footer.firstIndex() + i));
      }
    }
    if (firstPosition != footer.firstPosition()) {
      throw new LogObjectFormatException(
          "first position %d does not match the footer's %d"
              .formatted(firstPosition, footer.firstPosition()));
    }
    return frames;
  }

  /**
   * Reads the entries of one decompressed frame into {@code frames}.
   *
   * @return the asqn of the first application entry in the frame, -1 if it has none
   */
  private static long decodeFrame(
      final byte[] entries,
      final SBESerializer serializer,
      final long asqnBefore,
      final List<LogArchiveFrame> frames) {
    final var buffer = new UnsafeBuffer(entries);
    final int metadataLength = serializer.getMetadataLength();
    int offset = 0;
    long previousAsqn = asqnBefore;
    long firstAsqn = -1;
    try {
      while (offset < entries.length) {
        if (entries[offset] != JOURNAL_FRAME_VERSION) {
          throw new LogObjectFormatException(
              "invalid frame version %d at body offset %d".formatted(entries[offset], offset));
        }
        offset += 1;
        if (offset + metadataLength > entries.length) {
          throw new LogObjectFormatException("truncated entry metadata at body offset " + offset);
        }
        final var metadata = serializer.readMetadata(buffer, offset);
        offset += metadataLength;
        final int length = metadata.length();
        if (length < MIN_RECORD_DATA_LENGTH || offset + (long) length > entries.length) {
          throw new LogObjectFormatException(
              "invalid entry length %d at body offset %d".formatted(length, offset));
        }
        final byte[] record = Arrays.copyOfRange(entries, offset, offset + length);
        final var data = serializer.readData(new UnsafeBuffer(record), 0);
        offset += length;
        final long asqn = data.asqn();
        if (asqn >= 0) {
          if (asqn <= previousAsqn) {
            throw new LogObjectFormatException(
                "asqn %d does not increase at index %d".formatted(asqn, data.index()));
          }
          previousAsqn = asqn;
          if (firstAsqn < 0) {
            firstAsqn = asqn;
          }
        }
        frames.add(
            new LogArchiveFrame(data.index(), -1, asqn, asqn, -1, metadata.checksum(), record));
      }
    } catch (final LogObjectFormatException e) {
      throw e;
    } catch (final RuntimeException e) {
      throw new LogObjectFormatException("invalid entry", e);
    }
    return firstAsqn;
  }

  private static byte[] decompress(final byte[] object, final int offset, final int length) {
    final long size = Zstd.getFrameContentSize(object, offset, length);
    if (size < 0 || size > MAX_BODY_BYTES) {
      throw new LogObjectFormatException("invalid or unknown decompressed frame size " + size);
    }
    try (final var context = new ZstdDecompressCtx()) {
      final byte[] entries = new byte[(int) size];
      final int decompressed =
          context.decompressByteArray(entries, 0, entries.length, object, offset, length);
      if (decompressed != entries.length) {
        throw new LogObjectFormatException(
            "frame decompressed to %d bytes, expected %d".formatted(decompressed, size));
      }
      return entries;
    } catch (final ZstdException e) {
      throw new LogObjectFormatException("corrupt body", e);
    }
  }
}
