/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.management;

import io.atomix.raft.RaftServer.Role;
import io.zeebe.broker.exporter.stream.ExporterPhase;
import io.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;

public final class PartitionStatus {

  private final Role role;
  private final String snapshotId;
  private final Long processedPosition;
  private final Long processedPositionInSnapshot;
  private final Phase streamProcessorPhase;
  private final ExporterPhase exporterPhase;
  private final Long exportedPosition;

  private PartitionStatus(
      final Role role,
      final Long processedPosition,
      final String snapshotId,
      final Long processedPositionInSnapshot,
      final Phase streamProcessorPhase,
      final ExporterPhase exporterPhase,
      final Long exportedPosition) {
    this.role = role;
    this.processedPosition = processedPosition;
    this.snapshotId = snapshotId;
    this.processedPositionInSnapshot = processedPositionInSnapshot;
    this.streamProcessorPhase = streamProcessorPhase;
    this.exporterPhase = exporterPhase;
    this.exportedPosition = exportedPosition;
  }

  public static PartitionStatus ofLeader(
      final Long processedPosition,
      final String snapshotId,
      final Long processedPositionInSnapshot,
      final Phase streamProcessorPhase,
      final ExporterPhase exporterPhase,
      final long exportedPosition) {
    return new PartitionStatus(
        Role.LEADER,
        processedPosition,
        snapshotId,
        processedPositionInSnapshot,
        streamProcessorPhase,
        exporterPhase,
        exportedPosition);
  }

  public static PartitionStatus ofFollower(final String snapshotId) {
    return new PartitionStatus(Role.FOLLOWER, null, snapshotId, null, null, null, null);
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

  public ExporterPhase getExporterPhase() {
    return exporterPhase;
  }

  public Long getExportedPosition() {
    return exportedPosition;
  }
}
