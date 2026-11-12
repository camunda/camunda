/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Opens the state of one partition in a relational database. The state lives in the database, so
 * the directories given to this factory are not used.
 */
public final class RdbmsZeebeDbFactory<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyNames> {

  private final RdbmsStorage storage;
  private final long stateId;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final Supplier<MeterRegistry> meterRegistrySupplier;

  /**
   * @param stateId the partition's state; every database opened by this factory reads and writes it
   */
  public RdbmsZeebeDbFactory(
      final RdbmsStorage storage,
      final long stateId,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final Supplier<MeterRegistry> meterRegistrySupplier) {
    this.storage = Objects.requireNonNull(storage, "must specify a storage");
    this.stateId = stateId;
    this.consistencyChecksSettings =
        Objects.requireNonNull(consistencyChecksSettings, "must specify the consistency checks");
    this.meterRegistrySupplier =
        Objects.requireNonNull(meterRegistrySupplier, "must specify a meter registry supplier");
  }

  /** Same as {@link #createDb(File)}: flushing is a RocksDB setting. */
  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName, final boolean avoidFlush) {
    return createDb(pathName);
  }

  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName) {
    return new RdbmsZeebeDb<>(
        storage, stateId, consistencyChecksSettings, meterRegistrySupplier.get());
  }

  @Override
  public ZeebeDb<ColumnFamilyNames> openSnapshotOnlyDb(final File path) {
    throw new UnsupportedOperationException(
        "Expected to open a snapshot of the partition state, but the relational storage can't yet");
  }
}
