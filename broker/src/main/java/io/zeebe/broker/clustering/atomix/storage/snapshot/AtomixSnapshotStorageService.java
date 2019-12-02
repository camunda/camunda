/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorageReader;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class AtomixSnapshotStorageService implements Service<SnapshotStorage> {
  private static final String RUNTIME_DIRECTORY = "runtime";

  private final RaftPartition partition;
  private final int maxSnapshotsCount;

  private AtomixSnapshotStorage snapshotStorage;

  public AtomixSnapshotStorageService(final RaftPartition partition, final int maxSnapshotsCount) {
    this.partition = partition;
    this.maxSnapshotsCount = maxSnapshotsCount;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    snapshotStorage = createSnapshotStorage();
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    if (snapshotStorage != null) {
      snapshotStorage.close();
    }
  }

  @Override
  public AtomixSnapshotStorage get() {
    return snapshotStorage;
  }

  private AtomixSnapshotStorage createSnapshotStorage() {
    final var reader =
        new AtomixLogStorageReader(partition.getServer().openReader(-1, Mode.COMMITS));
    final var runtimeDirectory = partition.dataDirectory().toPath().resolve(RUNTIME_DIRECTORY);
    return new AtomixSnapshotStorage(
        runtimeDirectory,
        partition.getServer().getSnapshotStore(),
        new AtomixRecordEntrySupplierImpl(reader), // NOSONAR
        maxSnapshotsCount);
  }
}
