/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.util;

import io.camunda.zeebe.db.layered.BytesStore;
import io.camunda.zeebe.db.layered.PersistBatch;
import io.camunda.zeebe.db.layered.PersistSink;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import io.camunda.zeebe.db.layered.SnapshotSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * An in-memory stand-in for the durable side of the layered store — {@link PersistSink}, {@link
 * SnapshotSource} and per-store {@link BytesStore}s over one set of sorted maps — so the layered
 * machinery is testable without RocksDB.
 *
 * <p>Batches commit atomically under the state lock; {@link #failNextCommit()} makes the next
 * commit throw <em>before</em> applying anything, for atomicity tests. Snapshots are deep copies
 * taken under the lock — semantically equivalent to a pinned sequence number.
 */
public final class InMemoryDurableState {

  private final Map<String, TreeMap<byte[], byte[]>> stores = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private long anchor = -1;
  private boolean failNextCommit;

  private TreeMap<byte[], byte[]> storeMap(final String name) {
    return stores.computeIfAbsent(name, unused -> new TreeMap<>(Arrays::compareUnsigned));
  }

  /** The next {@link PersistBatch#commit()} throws before applying anything. */
  public void failNextCommit() {
    synchronized (lock) {
      failNextCommit = true;
    }
  }

  /** The committed value in {@code storeName}, for direct test assertions. */
  public byte[] committedValue(final String storeName, final byte[] key) {
    synchronized (lock) {
      return storeMap(storeName).get(key);
    }
  }

  /** The number of committed entries in {@code storeName}, for direct test assertions. */
  public int committedSize(final String storeName) {
    synchronized (lock) {
      return storeMap(storeName).size();
    }
  }

  /** A {@link BytesStore} over the named store's committed state. */
  public BytesStore store(final String name) {
    return new BytesStore() {
      @Override
      public byte[] get(final byte[] key) {
        synchronized (lock) {
          return storeMap(name).get(key);
        }
      }

      @Override
      public void put(final byte[] key, final byte[] value) {
        synchronized (lock) {
          storeMap(name).put(key, value);
        }
      }

      @Override
      public void delete(final byte[] key) {
        synchronized (lock) {
          storeMap(name).remove(key);
        }
      }

      @Override
      public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
        final List<Map.Entry<byte[], byte[]>> matches;
        synchronized (lock) {
          matches = new ArrayList<>(prefixSelection(storeMap(name), prefix).entrySet());
        }
        matches.forEach(entry -> visitor.accept(entry.getKey(), entry.getValue()));
      }
    };
  }

  public PersistSink sink() {
    return new PersistSink() {
      @Override
      public PersistBatch newBatch() {
        return new InMemoryBatch();
      }

      @Override
      public long readAnchor() {
        synchronized (lock) {
          return anchor;
        }
      }
    };
  }

  public SnapshotSource snapshotSource() {
    return () -> {
      final Map<String, TreeMap<byte[], byte[]>> copy = new HashMap<>();
      synchronized (lock) {
        stores.forEach(
            (name, map) -> copy.put(name, new TreeMap<>(map) /* comparator carries over */));
      }
      return new ReadSnapshot() {
        @Override
        public byte[] get(final String storeName, final byte[] key) {
          final TreeMap<byte[], byte[]> map = copy.get(storeName);
          return map == null ? null : map.get(key);
        }

        @Override
        public void prefixScan(
            final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
          final TreeMap<byte[], byte[]> map = copy.get(storeName);
          if (map != null) {
            prefixSelection(map, prefix).forEach(visitor);
          }
        }

        @Override
        public void close() {}
      };
    };
  }

  private static TreeMap<byte[], byte[]> prefixSelection(
      final TreeMap<byte[], byte[]> map, final byte[] prefix) {
    if (prefix.length == 0) {
      return map;
    }
    final byte[] upper = successor(prefix);
    final TreeMap<byte[], byte[]> selection = new TreeMap<>(Arrays::compareUnsigned);
    selection.putAll(
        upper == null ? map.tailMap(prefix, true) : map.subMap(prefix, true, upper, false));
    return selection;
  }

  /** The smallest key greater than every key prefixed by {@code prefix}; null for all-0xFF. */
  private static byte[] successor(final byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final byte[] upper = Arrays.copyOf(prefix, i + 1);
        upper[i]++;
        return upper;
      }
    }
    return null;
  }

  private final class InMemoryBatch implements PersistBatch {

    private final List<StagedWrite> writes = new ArrayList<>();
    private long stagedAnchor = -1;
    private boolean committed;

    @Override
    public void put(final String storeName, final byte[] key, final byte[] value) {
      writes.add(new StagedWrite(storeName, key, value));
    }

    @Override
    public void delete(final String storeName, final byte[] key) {
      writes.add(new StagedWrite(storeName, key, null));
    }

    @Override
    public void putAnchor(final long position) {
      if (stagedAnchor != -1) {
        throw new IllegalStateException("anchor already staged: " + stagedAnchor);
      }
      stagedAnchor = position;
    }

    @Override
    public void commit() {
      synchronized (lock) {
        if (failNextCommit) {
          failNextCommit = false;
          throw new IllegalStateException("injected commit failure");
        }
        for (final StagedWrite write : writes) {
          if (write.value == null) {
            storeMap(write.storeName).remove(write.key);
          } else {
            storeMap(write.storeName).put(write.key, write.value);
          }
        }
        if (stagedAnchor != -1) {
          anchor = stagedAnchor;
        }
        committed = true;
      }
    }

    @Override
    public void close() {
      if (!committed) {
        writes.clear();
      }
    }

    private record StagedWrite(String storeName, byte[] key, byte[] value) {}
  }
}
