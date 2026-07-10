/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.BytesStore;
import io.camunda.zeebe.db.layered.PersistSink;
import io.camunda.zeebe.db.layered.SnapshotSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

/**
 * The RocksDB-backed durable side of a layered state store: one open database whose column families
 * are the named {@link BytesStore}s, plus a dedicated anchor column family for the recovery anchor.
 * Routes store names to column family handles and hands out the {@link PersistSink} and {@link
 * SnapshotSource} adapters over the same database.
 *
 * <p>A backing created via {@link #open(Path, List)} owns the database, the column family handles
 * and the options, and releases them all on {@link #close()}. A backing created via the public
 * constructor adapts an externally owned database and only releases the resources it created
 * itself.
 */
public final class RocksDbLayeredBacking implements AutoCloseable {

  /** Name of the dedicated column family holding the recovery anchor. */
  public static final String ANCHOR_COLUMN_FAMILY = "layered-anchor";

  private static final String DEFAULT_COLUMN_FAMILY =
      new String(RocksDB.DEFAULT_COLUMN_FAMILY, StandardCharsets.UTF_8);

  private final RocksDB db;
  private final Map<String, ColumnFamilyHandle> storeHandles;
  private final Map<String, BytesStore> stores;
  private final RocksDbPersistSink sink;
  private final List<AutoCloseable> ownedResources;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Adapts an externally owned, already open database. The caller keeps ownership of the database
   * and the handles; this backing only owns the write options it creates internally.
   */
  public RocksDbLayeredBacking(
      final RocksDB db,
      final Map<String, ColumnFamilyHandle> storeHandles,
      final ColumnFamilyHandle anchorHandle) {
    this(db, storeHandles, anchorHandle, List.of());
  }

  private RocksDbLayeredBacking(
      final RocksDB db,
      final Map<String, ColumnFamilyHandle> storeHandles,
      final ColumnFamilyHandle anchorHandle,
      final List<AutoCloseable> ownedDbResources) {
    this.db = db;
    this.storeHandles = Map.copyOf(storeHandles);

    final Map<String, BytesStore> storeAdapters = new HashMap<>();
    this.storeHandles.forEach(
        (name, handle) -> storeAdapters.put(name, new RocksDbBytesStore(db, name, handle)));
    stores = Map.copyOf(storeAdapters);

    final WriteOptions writeOptions = new WriteOptions();
    sink = new RocksDbPersistSink(db, writeOptions, this.storeHandles, anchorHandle);

    // close order matters for native resources: write options first (independent of the db), then
    // the db-owned resources in the order the open() helper arranged them (handles, db, options)
    final List<AutoCloseable> owned = new ArrayList<>();
    owned.add(writeOptions);
    owned.addAll(ownedDbResources);
    ownedResources = List.copyOf(owned);
  }

  /**
   * Creates (or reopens) a database at {@code dir} with one column family per store name plus the
   * {@value #ANCHOR_COLUMN_FAMILY} column family. The returned backing owns the database and all
   * options and releases them on {@link #close()}.
   */
  public static RocksDbLayeredBacking open(final Path dir, final List<String> storeNames) {
    validateStoreNames(storeNames);

    final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
    final DBOptions dbOptions =
        new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
    try {
      final List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
      descriptors.add(
          new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
      for (final String storeName : storeNames) {
        descriptors.add(
            new ColumnFamilyDescriptor(
                storeName.getBytes(StandardCharsets.UTF_8), columnFamilyOptions));
      }
      descriptors.add(
          new ColumnFamilyDescriptor(
              ANCHOR_COLUMN_FAMILY.getBytes(StandardCharsets.UTF_8), columnFamilyOptions));

      final List<ColumnFamilyHandle> handles = new ArrayList<>();
      final RocksDB db = RocksDB.open(dbOptions, dir.toString(), descriptors, handles);

      final Map<String, ColumnFamilyHandle> storeHandles = new HashMap<>();
      for (int i = 0; i < storeNames.size(); i++) {
        storeHandles.put(storeNames.get(i), handles.get(i + 1));
      }
      final ColumnFamilyHandle anchorHandle = handles.get(handles.size() - 1);

      // handles must be closed before the db, the db before its options
      final List<AutoCloseable> ownedDbResources = new ArrayList<>(handles);
      ownedDbResources.add(db);
      ownedDbResources.add(dbOptions);
      ownedDbResources.add(columnFamilyOptions);

      return new RocksDbLayeredBacking(db, storeHandles, anchorHandle, ownedDbResources);
    } catch (final RocksDBException e) {
      CloseHelper.quietCloseAll(dbOptions, columnFamilyOptions);
      throw new ZeebeDbException("Failed to open layered RocksDB backing at " + dir, e);
    }
  }

  private static void validateStoreNames(final List<String> storeNames) {
    if (new HashSet<>(storeNames).size() != storeNames.size()) {
      throw new IllegalArgumentException("Duplicate store names in " + storeNames);
    }
    for (final String storeName : storeNames) {
      if (ANCHOR_COLUMN_FAMILY.equals(storeName) || DEFAULT_COLUMN_FAMILY.equals(storeName)) {
        throw new IllegalArgumentException(
            "Store name '%s' is reserved; choose a different name".formatted(storeName));
      }
    }
  }

  /** The committed-state view of the named store. */
  public BytesStore store(final String name) {
    final BytesStore store = stores.get(name);
    if (store == null) {
      throw new IllegalArgumentException(
          "Unknown store '%s'; known stores: %s".formatted(name, stores.keySet()));
    }
    return store;
  }

  public PersistSink sink() {
    return sink;
  }

  public SnapshotSource snapshotSource() {
    return () -> new RocksDbReadSnapshot(db, storeHandles);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      CloseHelper.quietCloseAll(ownedResources);
    }
  }
}
