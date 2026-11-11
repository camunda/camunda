/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawDbAccess;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Backend} over a {@link RawDbAccess}, for example RocksDB: keys are the raw bytes, the
 * group of a key is its column family, and values are encoded when the writer flushes them.
 */
final class RawBackend implements Backend<RawKey, DbValue> {

  private static final int ENTRY_OVERHEAD = 64;

  /**
   * Fixed guess of a value's size. Measuring the real size means encoding the value, which is the
   * work the write-behind store exists to avoid.
   */
  private static final int ESTIMATED_VALUE_BYTES = 256;

  private static final int LAST_KEY_SUFFIX_LENGTH = 256;

  private final RawDbAccess storage;

  // only the writer thread encodes
  private final ExpandableArrayBuffer encodeBuffer = new ExpandableArrayBuffer();

  RawBackend(final RawDbAccess storage) {
    this.storage = storage;
  }

  /** The range of the keys that start with the first {@code length} bytes of {@code prefix}. */
  static KeyRange<RawKey> prefixRange(final byte[] prefix, final int length) {
    return new KeyRange<>(RawKey.copyOf(prefix, length), RawKey.prefixEnd(prefix, length));
  }

  @Override
  public long groupOf(final RawKey key) {
    return key.columnFamily();
  }

  @Override
  public KeyRange<RawKey> groupRange(final long group) {
    final var prefix = prefixOf(group);
    return prefixRange(prefix, prefix.length);
  }

  @Override
  public RawKey groupLastSeekKey(final long group) {
    // The storage iterators only see keys with the seek key's column family prefix, so the seek
    // key must stay in the column family. No real key has this many 0xFF bytes after the prefix.
    final var prefix = prefixOf(group);
    final var last = Arrays.copyOf(prefix, prefix.length + LAST_KEY_SUFFIX_LENGTH);
    Arrays.fill(last, prefix.length, last.length, (byte) 0xFF);
    return RawKey.of(last);
  }

  private static byte[] prefixOf(final long columnFamily) {
    final var prefix = new byte[Long.BYTES];
    ByteBuffer.wrap(prefix)
        .order(io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER)
        .putLong(columnFamily);
    return prefix;
  }

  @Override
  public @Nullable DbValue get(final RawKey key, final DbValue prototype) {
    final var bytes = storage.get(key.bytes(), key.bytes().length);
    return bytes == null ? null : Values.decode(bytes, prototype);
  }

  @Override
  public boolean exists(final RawKey key) {
    return storage.get(key.bytes(), key.bytes().length) != null;
  }

  @Override
  public boolean mayExist(final RawKey key) {
    return storage.keyMayExist(key.bytes(), key.bytes().length);
  }

  @Override
  public Backend.Batch<RawKey, DbValue> newBatch() {
    return new Batch(storage.newBatch());
  }

  @Override
  public Backend.Cursor<RawKey, DbValue> newCursor() {
    return new Cursor(storage.newIterator());
  }

  @Override
  public void copy(final DbValue source, final DbValue target) {
    Values.copy(source, target);
  }

  @Override
  public int weigh(final RawKey key, final @Nullable DbValue value) {
    return ENTRY_OVERHEAD + key.bytes().length + (value == null ? 0 : ESTIMATED_VALUE_BYTES);
  }

  private final class Batch implements Backend.Batch<RawKey, DbValue> {

    private final RawDbAccess.RawBatch batch;

    private Batch(final RawDbAccess.RawBatch batch) {
      this.batch = batch;
    }

    @Override
    public void put(final RawKey key, final DbValue value) {
      batch.put(key.bytes(), Values.encode(value, encodeBuffer));
    }

    @Override
    public void delete(final RawKey key) {
      batch.delete(key.bytes());
    }

    @Override
    public void write() {
      batch.write();
    }

    @Override
    public void close() {
      batch.close();
    }
  }

  private static final class Cursor implements Backend.Cursor<RawKey, DbValue> {

    private final DbIterator iterator;

    private Cursor(final DbIterator iterator) {
      this.iterator = iterator;
    }

    @Override
    public void seek(final RawKey key) {
      iterator.seek(key.bytes());
    }

    @Override
    public void seekForPrev(final RawKey key) {
      iterator.seekForPrev(key.bytes());
    }

    @Override
    public boolean isValid() {
      return iterator.isValid();
    }

    @Override
    public void next() {
      iterator.next();
    }

    @Override
    public void prev() {
      iterator.prev();
    }

    @Override
    public RawKey key() {
      return RawKey.of(iterator.key());
    }

    @Override
    public void readValue(final DbValue target) {
      final var bytes = iterator.value();
      target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    }

    @Override
    public void close() {
      iterator.close();
    }
  }
}
