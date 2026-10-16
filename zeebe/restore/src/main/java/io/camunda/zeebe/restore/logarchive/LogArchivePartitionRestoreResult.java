/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import java.util.Optional;

/**
 * How one partition was restored.
 *
 * @param partitionId the restored partition
 * @param checkpointId the checkpoint it was restored to
 * @param checkpointPosition the checkpoint's position on this partition, the asqn of the last
 *     journal entry
 * @param snapshotId the installed snapshot, empty if the journal starts at index 1
 * @param firstIndex the first index of the rebuilt journal
 * @param lastIndex the last index of the rebuilt journal
 * @param timelineId the new timeline written into the partition's timeline file
 */
public record LogArchivePartitionRestoreResult(
    int partitionId,
    long checkpointId,
    long checkpointPosition,
    Optional<String> snapshotId,
    long firstIndex,
    long lastIndex,
    String timelineId) {}
