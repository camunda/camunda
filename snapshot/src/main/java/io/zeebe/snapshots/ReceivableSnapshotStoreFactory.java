/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import java.nio.file.Path;

/**
 * Creates a snapshot store which should store its {@link PersistedSnapshot} and {@link
 * ReceivedSnapshot } instances in the given directory.
 */
@FunctionalInterface
public interface ReceivableSnapshotStoreFactory {

  /**
   * Creates a snapshot store operating in the given {@code directory}.
   *
   * @param directory the root directory where snapshots should be stored
   * @param partitionId the id of the partition for this store
   * @return a new {@link PersistedSnapshotStore}
   */
  ReceivableSnapshotStore createReceivableSnapshotStore(Path directory, int partitionId);
}
