/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.PersistedSnapshotListener;
import io.zeebe.snapshots.raft.PersistedSnapshotStore;
import io.zeebe.snapshots.raft.SnapshotChunkReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NoopSnapshotStore implements PersistedSnapshotStore {

  private final List<PersistedSnapshotListener> listeners = new ArrayList<>();

  public void takeNewSnapshot(final long index) {
    listeners.forEach(
        l ->
            l.onNewSnapshot(
                new PersistedSnapshot() {
                  @Override
                  public WallClockTimestamp getTimestamp() {
                    return null;
                  }

                  @Override
                  public int version() {
                    return 0;
                  }

                  @Override
                  public long getIndex() {
                    return index;
                  }

                  @Override
                  public long getTerm() {
                    return 0;
                  }

                  @Override
                  public SnapshotChunkReader newChunkReader() {
                    return null;
                  }

                  @Override
                  public void delete() {}

                  @Override
                  public Path getPath() {
                    return null;
                  }

                  @Override
                  public long getCompactionBound() {
                    return index;
                  }

                  @Override
                  public String getId() {
                    return null;
                  }

                  @Override
                  public void close() {}
                }));
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.empty();
  }

  @Override
  public void purgePendingSnapshots() throws IOException {}

  @Override
  public void addSnapshotListener(final PersistedSnapshotListener listener) {
    this.listeners.add(listener);
  }

  @Override
  public void removeSnapshotListener(final PersistedSnapshotListener listener) {
    this.listeners.remove(listener);
  }

  @Override
  public long getCurrentSnapshotIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete() {}

  @Override
  public void close() {}
}
