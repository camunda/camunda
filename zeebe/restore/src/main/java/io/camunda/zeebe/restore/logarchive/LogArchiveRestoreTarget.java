/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.atomix.raft.partition.RaftPartition;
import java.nio.file.Path;

/**
 * Where one partition is restored to.
 *
 * @param partitionId the partition id
 * @param dataDirectory the partition's data directory; must be empty or not exist yet
 * @param journalName the journal segment name prefix, {@link RaftPartition#name()}
 * @param brokerId the id of the restoring broker, for the snapshot store
 */
public record LogArchiveRestoreTarget(
    int partitionId, Path dataDirectory, String journalName, int brokerId) {

  public static LogArchiveRestoreTarget of(final RaftPartition partition, final int brokerId) {
    return new LogArchiveRestoreTarget(
        partition.id().number(), partition.dataDirectory().toPath(), partition.name(), brokerId);
  }
}
