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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Opens the state of one partition in a relational database. The state lives in the database; the
 * directories given to this factory only hold the marker that a snapshot writes (see {@link
 * ZeebeDb#createSnapshot(File)}).
 */
public final class RdbmsZeebeDbFactory<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyNames> {

  private final RdbmsStorage storage;
  private final int partitionId;
  private final LongSupplier term;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final Supplier<MeterRegistry> meterRegistrySupplier;
  private final PartitionStates partitionStates;

  /**
   * @param partitionId the partition whose state every database opened by this factory reads and
   *     writes
   * @param term the term of the partition's leader, read every time a database is opened; that
   *     database writes with it until a leader of a newer term claims the state
   */
  public RdbmsZeebeDbFactory(
      final RdbmsStorage storage,
      final int partitionId,
      final LongSupplier term,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final Supplier<MeterRegistry> meterRegistrySupplier) {
    this.storage = Objects.requireNonNull(storage, "must specify a storage");
    this.partitionId = partitionId;
    this.term = Objects.requireNonNull(term, "must specify a term supplier");
    this.consistencyChecksSettings =
        Objects.requireNonNull(consistencyChecksSettings, "must specify the consistency checks");
    this.meterRegistrySupplier =
        Objects.requireNonNull(meterRegistrySupplier, "must specify a meter registry supplier");
    partitionStates = new PartitionStates(storage);
  }

  /** Same as {@link #createDb(File)}: flushing is a RocksDB setting. */
  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName, final boolean avoidFlush) {
    return createDb(pathName);
  }

  /**
   * Claims the partition's state with the current term. If the directory holds a snapshot marker,
   * the database must hold the state it names; otherwise the state is created if the partition has
   * none yet.
   *
   * @throws StateFencedException if a leader of a newer term owns the state
   * @throws IllegalStateException if the marker names another state
   */
  @Override
  public ZeebeDb<ColumnFamilyNames> createDb(final File pathName) {
    final long currentTerm = term.getAsLong();
    final var snapshotMarker = StateMarker.readIfPresent(pathName.toPath());
    snapshotMarker.ifPresent(this::checkPartition);
    final var stateId =
        partitionStates.attach(partitionId, snapshotMarker.map(StateMarker::stateId), currentTerm);
    return new RdbmsZeebeDb<>(
        storage,
        new StateMarker(partitionId, stateId),
        OptionalLong.of(currentTerm),
        consistencyChecksSettings,
        meterRegistrySupplier.get());
  }

  /**
   * Opens the partition's state for reading only, without claiming it, so the leader that owns it
   * keeps writing. Reads see what the leader has committed so far. A snapshot of it names the
   * state, like a snapshot of the leader's database.
   *
   * <p>Followers use it to take snapshots, which let them compact their log.
   *
   * @return the database, or empty if the partition has no state yet
   */
  public Optional<ZeebeDb<ColumnFamilyNames>> openReadOnlyDb() {
    return partitionStates
        .find(partitionId)
        .map(
            stateId ->
                new RdbmsZeebeDb<>(
                    storage,
                    new StateMarker(partitionId, stateId),
                    OptionalLong.empty(),
                    consistencyChecksSettings,
                    meterRegistrySupplier.get()));
  }

  /**
   * Reads the snapshot's marker, without touching the database.
   *
   * @throws IllegalStateException if the directory holds no valid marker of this partition
   */
  @Override
  public ZeebeDb<ColumnFamilyNames> openSnapshotOnlyDb(final File path) {
    final var snapshotMarker = StateMarker.readFrom(path.toPath());
    checkPartition(snapshotMarker);
    return new RdbmsSnapshotOnlyDb<>(snapshotMarker);
  }

  private void checkPartition(final StateMarker snapshotMarker) {
    if (snapshotMarker.partitionId() != partitionId) {
      throw new IllegalStateException(
          "Expected a snapshot of partition %d, but it names the state of partition %d"
              .formatted(partitionId, snapshotMarker.partitionId()));
    }
  }
}
