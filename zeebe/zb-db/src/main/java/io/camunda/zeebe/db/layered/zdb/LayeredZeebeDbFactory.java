/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import java.io.File;
import java.util.Objects;

/**
 * Decorates a {@link ZeebeDbFactory} so every runtime database it creates is wrapped in a {@link
 * LayeredZeebeDb}. Snapshot-only databases are <em>not</em> wrapped — they exist purely to copy
 * checkpointed files and never see the layered write path.
 *
 * <p>The wrapped databases use the unpinned fallback {@link
 * io.camunda.zeebe.db.layered.SnapshotSource}: sound as long as persist rounds run inline on the
 * owner thread and no asynchronous readers consume {@link
 * io.camunda.zeebe.db.layered.ReadOnlyView}s (see {@link UnpinnedSnapshotSource}) — which is
 * exactly the phase-A broker wiring, where all secondary consumers still read through pass-through
 * contexts. Wiring a pinning source requires access to the RocksDB handles and is deferred until
 * views gain async readers.
 */
public final class LayeredZeebeDbFactory<
        ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
    implements ZeebeDbFactory<ColumnFamilyType> {

  private final ZeebeDbFactory<ColumnFamilyType> delegate;
  private final LayeredZeebeDbConfig config;

  private LayeredZeebeDbFactory(
      final ZeebeDbFactory<ColumnFamilyType> delegate, final LayeredZeebeDbConfig config) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.config = Objects.requireNonNull(config, "config");
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
      LayeredZeebeDbFactory<ColumnFamilyType> of(
          final ZeebeDbFactory<ColumnFamilyType> delegate, final LayeredZeebeDbConfig config) {
    return new LayeredZeebeDbFactory<>(delegate, config);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName, final boolean avoidFlush) {
    return wrap(delegate.createDb(pathName, avoidFlush));
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    return wrap(delegate.createDb(pathName));
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    return delegate.openSnapshotOnlyDb(path);
  }

  private ZeebeDb<ColumnFamilyType> wrap(final ZeebeDb<ColumnFamilyType> inner) {
    return new LayeredZeebeDb<>(inner, config);
  }
}
