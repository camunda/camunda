/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import io.zeebe.util.CloseableSilently;
import java.nio.file.Path;

/** Represents a snapshot, which was persisted at the {@link PersistedSnapshotStore}. */
public interface PersistedSnapshot extends CloseableSilently {

  /**
   * Returns the snapshot format version.
   *
   * @return the snapshot format version
   */
  int version();

  /**
   * Returns the snapshot index.
   *
   * <p>The snapshot index is the index of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot index.
   */
  long getIndex();

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot term.
   */
  long getTerm();

  /**
   * Returns a new snapshot chunk reader for this snapshot. Chunk readers are meant to be one-time
   * use and as such don't have to be thread-safe.
   *
   * @return a new snapshot chunk reader
   */
  SnapshotChunkReader newChunkReader();

  /** Deletes the snapshot. */
  void delete();

  /** @return a path to the snapshot location */
  Path getPath();

  /**
   * Returns an implementation specific compaction bound, e.g. a log stream position, index etc.,
   * used during compaction
   *
   * @return the compaction upper bound
   */
  long getCompactionBound();

  /** @return the identifier of the snapshot */
  String getId();

  /**
   * Returns the checksum of the snapshot, which can be used to verify integrity.
   *
   * @return the checksum of the snapshot
   */
  long getChecksum();
}
