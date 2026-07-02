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

  private BatchReader() {}

  /** A decoded entry: its log position and copies of its key and value bytes. */
  public record Entry(long position, byte[] key, byte[] value) {}

  /**
   * Reads every entry at or after {@code fromPosition} from the batches in {@code data[offset,
   * offset+length)}. Batches whose version is unsupported, or that run past the region, stop the
   * scan (a partial trailing batch is ignored rather than throwing).
   */
  public static List<Entry> read(
      final byte[] data, final int offset, final int length, final long fromPosition) {
    final List<Entry> out = new ArrayList<>();
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
          final byte[] key = new byte[keyLength];
          System.arraycopy(data, keyOffset, key, 0, keyLength);
          final byte[] value = new byte[valueLength];
          System.arraycopy(data, valueOffset, value, 0, valueLength);
          out.add(new Entry(position, key, value));
        }
        entryOffset += BatchFormat.ENTRY_LENGTH_SIZE + entryLength;
      }
      batchOffset += totalSize;
    }
    return out;
  }
}
