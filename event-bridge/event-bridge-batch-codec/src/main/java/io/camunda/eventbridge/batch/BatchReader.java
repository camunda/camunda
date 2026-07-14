/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.batch;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JDK reader for EventBridge batches — the consumer side of the wire format, with no Agrona
 * dependency. Parses one or more concatenated batches from a {@code byte[]} region (the data
 * portion of a fetch response) into {@link Entry entries}, each carrying its log position and a
 * copy of its key/value.
 *
 * <p>Mirrors the broker-side iteration (block → batch → entry) but reads through {@link
 * BatchFormat}'s little-endian helpers and copies key/value into arrays, since a client has no
 * zero-copy buffer to lend.
 */
public final class BatchReader {

  private static final byte[] NO_KEY = new byte[0];

  private BatchReader() {}

  /** A decoded entry: its log position and copies of its key and value bytes. */
  public record Entry(long position, byte[] key, byte[] value) {}

  /**
   * Receives each entry in place during a {@link #forEachSkippingKeys} scan: its log position and
   * the coordinates of its value bytes inside the shared {@code data} array. Nothing is allocated
   * per entry — a visitor that retains the value must copy {@code data[valueOffset,
   * valueOffset+valueLength)} itself, since the array is the (reused) fetch response.
   */
  @FunctionalInterface
  public interface EntryVisitor {
    void visit(long position, byte[] data, int valueOffset, int valueLength);
  }

  /** Receives each entry's full in-place coordinates during the shared scan. */
  @FunctionalInterface
  private interface EntryScanVisitor {
    void visit(long position, int keyOffset, int keyLength, int valueOffset, int valueLength);
  }

  /**
   * Reads every entry at or after {@code fromPosition} from the batches in {@code data[offset,
   * offset+length)}. Batches whose version is unsupported, or that run past the region, stop the
   * scan (a partial trailing batch is ignored rather than throwing).
   */
  public static List<Entry> read(
      final byte[] data, final int offset, final int length, final long fromPosition) {
    return read(data, offset, length, fromPosition, true);
  }

  /**
   * Like {@link #read(byte[], int, int, long)}, but does not copy the per-entry key bytes: every
   * entry carries the same shared empty key. For consumers that only need positions and values
   * (e.g. the client fetch path), this skips one array copy per entry.
   */
  public static List<Entry> readSkippingKeys(
      final byte[] data, final int offset, final int length, final long fromPosition) {
    return read(data, offset, length, fromPosition, false);
  }

  /**
   * Visits every entry at or after {@code fromPosition} in place, in on-wire order — the cursor
   * counterpart of {@link #readSkippingKeys(byte[], int, int, long)} for hot consume paths: no
   * {@link Entry}, no list and no value copy are allocated per entry; the visitor decides what to
   * materialize. The same malformed-input rules apply (an unsupported version or a truncated
   * trailing batch stops the scan).
   */
  public static void forEachSkippingKeys(
      final byte[] data,
      final int offset,
      final int length,
      final long fromPosition,
      final EntryVisitor visitor) {
    scan(
        data,
        offset,
        length,
        fromPosition,
        (position, keyOffset, keyLength, valueOffset, valueLength) ->
            visitor.visit(position, data, valueOffset, valueLength));
  }

  private static List<Entry> read(
      final byte[] data,
      final int offset,
      final int length,
      final long fromPosition,
      final boolean copyKeys) {
    final List<Entry> out = new ArrayList<>();
    scan(
        data,
        offset,
        length,
        fromPosition,
        (position, keyOffset, keyLength, valueOffset, valueLength) -> {
          final byte[] key;
          if (copyKeys) {
            key = new byte[keyLength];
            System.arraycopy(data, keyOffset, key, 0, keyLength);
          } else {
            key = NO_KEY;
          }
          final byte[] value = new byte[valueLength];
          System.arraycopy(data, valueOffset, value, 0, valueLength);
          out.add(new Entry(position, key, value));
        });
    return out;
  }

  /** The single block → batch → entry walk both the list and the cursor APIs decode through. */
  private static void scan(
      final byte[] data,
      final int offset,
      final int length,
      final long fromPosition,
      final EntryScanVisitor visitor) {
    final int end = offset + length;
    int batchOffset = offset;

    while (batchOffset + BatchFormat.HEADER_LENGTH <= end) {
      final int version = BatchFormat.getIntLE(data, batchOffset + BatchFormat.VERSION_OFFSET);
      if (version != BatchFormat.VERSION_1) {
        break;
      }
      final long batchPosition =
          BatchFormat.getLongLE(data, batchOffset + BatchFormat.POSITION_OFFSET);
      final int entryCount =
          BatchFormat.getIntLE(data, batchOffset + BatchFormat.ENTRY_COUNT_OFFSET);
      final int batchLength =
          BatchFormat.getIntLE(data, batchOffset + BatchFormat.BATCH_LENGTH_OFFSET);
      final int totalSize = BatchFormat.totalSize(batchLength);
      if (batchOffset + totalSize > end) {
        break; // truncated trailing batch
      }

      int entryOffset = batchOffset + BatchFormat.HEADER_LENGTH;
      for (int i = 0; i < entryCount; i++) {
        if (entryOffset + BatchFormat.ENTRY_HEADER_SIZE > batchOffset + totalSize) {
          break;
        }
        final int entryLength = BatchFormat.getIntLE(data, entryOffset);
        final int keyLength =
            BatchFormat.getIntLE(data, entryOffset + BatchFormat.ENTRY_LENGTH_SIZE);
        final int valueLength = entryLength - BatchFormat.KEY_LENGTH_SIZE - keyLength;
        final int keyOffset = entryOffset + BatchFormat.ENTRY_HEADER_SIZE;
        final int valueOffset = keyOffset + keyLength;
        if (keyLength < 0
            || valueLength < 0
            || valueOffset + valueLength > batchOffset + totalSize) {
          break; // malformed
        }

        final long position = batchPosition + i;
        if (position >= fromPosition) {
          visitor.visit(position, keyOffset, keyLength, valueOffset, valueLength);
        }
        entryOffset += BatchFormat.ENTRY_LENGTH_SIZE + entryLength;
      }
      batchOffset += totalSize;
    }
  }
}
