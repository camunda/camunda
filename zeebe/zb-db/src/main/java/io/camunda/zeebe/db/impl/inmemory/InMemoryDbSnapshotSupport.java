/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.DbValue;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListMap;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Simple binary serialization for snapshotting the in-memory store. Serialization of values only
 * happens here — at snapshot time. Format: repeated [keyLen(4) | keyBytes | valueLen(4) |
 * valueBytes].
 */
final class InMemoryDbSnapshotSupport {

  private static final String SNAPSHOT_FILE = "inmemory.snapshot";

  private InMemoryDbSnapshotSupport() {}

  static void writeSnapshot(
      final ConcurrentSkipListMap<byte[], DbValue> data, final File snapshotDir) {
    final var snapshotFile = new File(snapshotDir, SNAPSHOT_FILE);
    final var buffer = new ExpandableArrayBuffer();
    try (final var out = new DataOutputStream(new FileOutputStream(snapshotFile))) {
      for (final var entry : data.entrySet()) {
        out.writeInt(entry.getKey().length);
        out.write(entry.getKey());
        final DbValue value = entry.getValue();
        final int valueLen = value.getLength();
        buffer.checkLimit(valueLen);
        value.write(buffer, 0);
        out.writeInt(valueLen);
        out.write(buffer.byteArray(), 0, valueLen);
      }
    } catch (final IOException e) {
      throw new RuntimeException("Failed to write in-memory snapshot", e);
    }
  }

  static void readSnapshot(
      final ConcurrentSkipListMap<byte[], DbValue> data, final File snapshotDir) {
    final var snapshotFile = new File(snapshotDir, SNAPSHOT_FILE);
    if (!snapshotFile.exists()) {
      return;
    }
    data.clear();
    try (final var in = new DataInputStream(new FileInputStream(snapshotFile))) {
      while (in.available() > 0) {
        final int keyLen = in.readInt();
        final byte[] key = new byte[keyLen];
        in.readFully(key);
        final int valueLen = in.readInt();
        final byte[] valueBytes = new byte[valueLen];
        in.readFully(valueBytes);
        // Restored values are raw bytes — the default copyTo (serialize+wrap) handles reads.
        data.put(key, new RestoredValue(new UnsafeBuffer(valueBytes)));
      }
    } catch (final IOException e) {
      throw new RuntimeException("Failed to read in-memory snapshot", e);
    }
  }

  /**
   * A bytes-backed DbValue used only for snapshot restore. It holds serialized bytes and relies on
   * the default {@link DbValue#copyTo} (serialize+wrap) to populate the real typed instance on
   * read. On the first write via the ColumnFamily, it gets replaced by a properly typed instance
   * created via {@link DbValue#newInstance()}.
   */
  static final class RestoredValue implements DbValue {
    private final DirectBuffer data;

    RestoredValue(final DirectBuffer data) {
      this.data = data;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      // Not needed — this is a read-only holder
    }

    @Override
    public int getLength() {
      return data.capacity();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, data, 0, data.capacity());
      return data.capacity();
    }
  }
}
