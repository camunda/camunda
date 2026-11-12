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
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Backend} over a {@link RawDbAccess}, for example RocksDB: keys are the raw bytes, the
 * group of a key is its column family, and the store holds values with their encoded bytes, which
 * the writer writes as they are.
 */
final class RawBackend implements Backend<RawKey, HeldValue, DbValue> {

  /**
   * Heap of an entry besides the key and value bytes: the cache or map node, the key, version and
   * held value objects, the array headers and the buffer that the held object wraps. The decoded
   * held object itself is not counted, as its size depends on the value class.
   */
  static final int ENTRY_OVERHEAD = 200;

  private final RawDbAccess storage;

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
  public @Nullable HeldValue get(final RawKey key, final DbValue prototype) {
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
  public Backend.Batch<RawKey, HeldValue> newBatch() {
    return new Batch(storage.newBatch());
  }

  @Override
  public Backend.Cursor<RawKey, DbValue> newCursor() {
    return new Cursor(storage.newIterator());
  }

  @Override
  public void copy(final HeldValue source, final DbValue target) {
    Values.copyInto(source, target);
  }

  @Override
  public int weigh(final RawKey key, final @Nullable HeldValue value) {
    return ENTRY_OVERHEAD + key.bytes().length + (value == null ? 0 : value.length());
  }

  private static final class Batch implements Backend.Batch<RawKey, HeldValue> {

    private final RawDbAccess.RawBatch batch;

    private Batch(final RawDbAccess.RawBatch batch) {
      this.batch = batch;
    }

    @Override
    public void put(final RawKey key, final HeldValue value) {
      batch.put(key.bytes(), value.bytes());
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
      // a fresh array per read, so the target may keep views of it
      final var bytes = iterator.value();
      target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    }

    @Override
    public void close() {
      iterator.close();
    }
  }
}
