/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import org.slf4j.Logger;

public interface RestoreFactory {

  /**
   * @param partitionId the ID of the partition from which the nodes will be provided
   * @return a node provider for the given partition
   */
  RestoreNodeProvider createNodeProvider(int partitionId);

  /** @return a configured {@link RestoreClient} */
  RestoreClient createClient(int partitionId);

  /** @return a {@link SnapshotRestoreContext} */
  SnapshotRestoreContext createSnapshotRestoreContext(int partitionId, Logger logger);
}
