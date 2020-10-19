/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker;

import io.zeebe.snapshots.raft.PersistedSnapshotStore;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;

public interface SnapshotStoreSupplier {

  /**
   * Returns a partition's {@link ConstructableSnapshotStore}
   *
   * @param partitionName
   * @return a ConstructableSnapshotStore
   */
  ConstructableSnapshotStore getConstructableSnapshotStore(String partitionName);

  /**
   * Returns a partition's {@link ReceivableSnapshotStore}
   *
   * @param partitionName
   * @return a ReceivableSnapshotStore
   */
  ReceivableSnapshotStore getReceivableSnapshotStore(final String partitionName);

  /**
   * Returns a partition's {@link PersistedSnapshotStore}
   *
   * @param partitionName
   * @return a PersistedSnapshotStore
   */
  PersistedSnapshotStore getPersistedSnapshotStore(String partitionName);
}
