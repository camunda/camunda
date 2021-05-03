/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

public interface ReceivableSnapshotStore extends PersistedSnapshotStore {

  /**
   * Starts a new received volatile snapshot which can be persisted later.
   *
   * @param snapshotId the snapshot id which is defined as {@code
   *     index-term-timestamp-processedposition-exportedposition}
   * @return the new volatile received snapshot
   */
  ReceivedSnapshot newReceivedSnapshot(String snapshotId);
}
