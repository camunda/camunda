/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreFactory;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import org.slf4j.Logger;

public class ReplicatingRestoreClientProvider implements RestoreFactory {

  private final RestoreClient restoreClient;
  private final SnapshotRestoreContext snapshotRestoreContext;

  public ReplicatingRestoreClientProvider(
      RestoreClient restoreClient, SnapshotRestoreContext restoreContext) {
    this.restoreClient = restoreClient;
    this.snapshotRestoreContext = restoreContext;
  }

  @Override
  public RestoreNodeProvider createNodeProvider(int partitionId) {
    return () -> MemberId.from("1");
  }

  @Override
  public RestoreClient createClient(int partitionId) {
    return restoreClient;
  }

  @Override
  public SnapshotRestoreContext createSnapshotRestoreContext(int partitionId, Logger logger) {
    return this.snapshotRestoreContext;
  }
}
