/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

public interface SnapshotStoreSupplier {

  /**
   * Returns a partition's {@link ConstructableSnapshotStore}
   *
   * @param partitionId
   * @return a ConstructableSnapshotStore
   */
  ConstructableSnapshotStore getConstructableSnapshotStore(int partitionId);

  /**
   * Returns a partition's {@link ReceivableSnapshotStore}
   *
   * @param partitionId
   * @return a ReceivableSnapshotStore
   */
  ReceivableSnapshotStore getReceivableSnapshotStore(final int partitionId);

  /**
   * Returns a partition's {@link PersistedSnapshotStore}
   *
   * @param partitionId
   * @return a PersistedSnapshotStore
   */
  PersistedSnapshotStore getPersistedSnapshotStore(int partitionId);
}
