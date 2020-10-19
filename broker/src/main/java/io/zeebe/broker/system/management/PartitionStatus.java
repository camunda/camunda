/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.atomix.raft.RaftServer.Role;
import io.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;

public final class PartitionStatus {

  private final Role role;
  private final String snapshotId;

  private final Long processedPosition;

  private final Long processedPositionInSnapshot;

  private final Phase streamProcessorPhase;

  private PartitionStatus(
      final Role role,
      final Long processedPosition,
      final String snapshotId,
      final Long processedPositionInSnapshot,
      final Phase streamProcessorPhase) {
    this.role = role;
    this.processedPosition = processedPosition;
    this.snapshotId = snapshotId;
    this.processedPositionInSnapshot = processedPositionInSnapshot;
    this.streamProcessorPhase = streamProcessorPhase;
  }

  public static PartitionStatus ofLeader(
      final Long processedPosition,
      final String snapshotId,
      final Long processedPositionInSnapshot,
      final Phase streamProcessorPhase) {
    return new PartitionStatus(
        Role.LEADER,
        processedPosition,
        snapshotId,
        processedPositionInSnapshot,
        streamProcessorPhase);
  }

  public static PartitionStatus ofFollower(final String snapshotId) {
    return new PartitionStatus(Role.FOLLOWER, null, snapshotId, null, null);
  }

  public Role getRole() {
    return role;
  }

  public Long getProcessedPosition() {
    return processedPosition;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public Long getProcessedPositionInSnapshot() {
    return processedPositionInSnapshot;
  }

  public Phase getStreamProcessorPhase() {
    return streamProcessorPhase;
  }
}
