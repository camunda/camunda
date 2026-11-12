/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.util.function.Supplier;

/**
 * Wraps another {@link ZeebeDbFactory} so that the databases it creates for the runtime state go
 * through a write-behind cache.
 *
 * <p>Only {@link #createDb(File)}, which opens the runtime state, wraps. The other two delegate:
 * they open databases that are copied or snapshotted in bulk, where a cache doesn't help.
 */
public final class WriteBehindZeebeDbFactory<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyNames> {

  private final ZeebeDbFactory<ColumnFamilyNames> storage;
  private final WriteBehindConfiguration configuration;
  private final Supplier<? extends CommitGate> commitGates;
  private final ConsistencyChecksSettings consistencyChecksSettings;

  /**
   * Creates a factory for a storage that only this replica uses, like RocksDB, where the writer
   * doesn't wait for the log.
   *
   * @param consistencyChecksSettings the checks the cache runs; pass the same settings as to the
   *     storage, whose own checks can't see the writes the cache hasn't written yet
   */
  public WriteBehindZeebeDbFactory(
      final ZeebeDbFactory<ColumnFamilyNames> storage,
      final WriteBehindConfiguration configuration,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this(storage, configuration, () -> NoCommitGate.INSTANCE, consistencyChecksSettings);
  }

  /**
   * Creates a factory whose databases only write a transaction to the storage once their commit
   * gate reports that the log committed its records. Each database asks {@code commitGates} for its
   * own gate when it opens, since a gate only follows the log while the leader that opened the
   * database leads.
   *
   * @param consistencyChecksSettings the checks the cache runs; pass the same settings as to the
   *     storage, whose own checks can't see the writes the cache hasn't written yet
   */
  public WriteBehindZeebeDbFactory(
      final ZeebeDbFactory<ColumnFamilyNames> storage,
      final WriteBehindConfiguration configuration,
      final Supplier<? extends CommitGate> commitGates,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.storage = storage;
    this.configuration = configuration;
    this.commitGates = commitGates;
    this.consistencyChecksSettings = consistencyChecksSettings;
  }

  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName, final boolean avoidFlush) {
    return storage.createDb(pathName, avoidFlush);
  }

  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName) {
    final CommitGate commitGate = commitGates.get();
    final var storageDb = storage.createDb(pathName);
    try {
      return new WriteBehindZeebeDb<>(
          storageDb, configuration, commitGate, consistencyChecksSettings);
    } catch (final RuntimeException e) {
      // the caller only gets the exception, so nothing else would close the storage
      try {
        storageDb.close();
      } catch (final Exception closeError) {
        e.addSuppressed(closeError);
      }
      throw e;
    }
  }

  @Override
  public ZeebeDb<ColumnFamilyNames> openSnapshotOnlyDb(final File path) {
    return storage.openSnapshotOnlyDb(path);
  }
}
