/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.RawDbAccess;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A sorted in-memory storage, so that tests of the store need no real database. Its iterators show
 * the state at the time they were created, like a RocksDB iterator.
 */
final class InMemoryRawDbAccess implements RawDbAccess {

  private final NavigableMap<byte[], byte[]> entries = new TreeMap<>(Arrays::compareUnsigned);

  @Override
  public synchronized byte[] get(final byte[] key, final int keyLength) {
    return entries.get(Arrays.copyOf(key, keyLength));
  }

  @Override
  public synchronized boolean keyMayExist(final byte[] key, final int keyLength) {
    return entries.containsKey(Arrays.copyOf(key, keyLength));
  }

  @Override
  public synchronized DbIterator newIterator() {
    return new Iterator(new TreeMap<>(entries));
  }

  @Override
  public RawBatch newBatch() {
    return new Batch();
  }

  @Override
  public void createCheckpoint(final File directory) {
    throw new UnsupportedOperationException("Checkpoints are not supported by this test storage");
  }

  private final class Batch implements RawBatch {

    private final NavigableMap<byte[], byte[]> changes = new TreeMap<>(Arrays::compareUnsigned);

    @Override
    public void put(final byte[] key, final byte[] value) {
      changes.put(key.clone(), value.clone());
    }

    @Override
    public void delete(final byte[] key) {
      changes.put(key.clone(), null);
    }

    @Override
    public void write() {
      synchronized (InMemoryRawDbAccess.this) {
        changes.forEach(
            (key, value) -> {
              if (value == null) {
                entries.remove(key);
              } else {
                entries.put(key, value);
              }
            });
      }
    }

    @Override
    public void close() {}
  }

  private static final class Iterator implements DbIterator {

    private final NavigableMap<byte[], byte[]> snapshot;
    private Map.Entry<byte[], byte[]> current;

    private Iterator(final NavigableMap<byte[], byte[]> snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public void seek(final byte[] key) {
      current = snapshot.ceilingEntry(key);
    }

    @Override
    public void seekForPrev(final byte[] key) {
      current = snapshot.floorEntry(key);
    }

    @Override
    public boolean isValid() {
      return current != null;
    }

    @Override
    public void next() {
      current = snapshot.higherEntry(current.getKey());
    }

    @Override
    public void prev() {
      current = snapshot.lowerEntry(current.getKey());
    }

    @Override
    public byte[] key() {
      return current.getKey().clone();
    }

    @Override
    public byte[] value() {
      return current.getValue().clone();
    }

    @Override
    public int key(final byte[] buffer) {
      return copy(current.getKey(), buffer);
    }

    @Override
    public int value(final byte[] buffer) {
      return copy(current.getValue(), buffer);
    }

    private static int copy(final byte[] source, final byte[] buffer) {
      System.arraycopy(source, 0, buffer, 0, Math.min(source.length, buffer.length));
      return source.length;
    }

    @Override
    public void close() {}
  }
}
