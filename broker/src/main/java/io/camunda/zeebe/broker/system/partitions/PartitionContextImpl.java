/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import java.io.IOException;

public class PartitionContextImpl implements PartitionContext {

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final AsyncSnapshotDirector snapshotDirector;
  private final StreamProcessor streamProcessor;
  private final ExporterDirector exporterDirector;
  private final PartitionProcessingState partitionProcessingState;

  PartitionContextImpl(
      final PartitionTransitionContext transitionContext,
      final PartitionProcessingState partitionProcessingState) {
    partitionId = transitionContext.getPartitionId();
    raftPartition = transitionContext.getRaftPartition();
    snapshotDirector = transitionContext.getSnapshotDirector();
    streamProcessor = transitionContext.getStreamProcessor();
    exporterDirector = transitionContext.getExporterDirector();
    this.partitionProcessingState = partitionProcessingState;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  @Override
  public void triggerSnapshot() {
    if (snapshotDirector != null) {
      snapshotDirector.forceSnapshot();
    }
  }

  @Override
  public Role getCurrentRole() {
    return raftPartition.getRole();
  }

  @Override
  public long getCurrentTerm() {
    return raftPartition.term();
  }

  @Override
  public StreamProcessor getStreamProcessor() {
    return streamProcessor;
  }

  @Override
  public ExporterDirector getExporterDirector() {
    return exporterDirector;
  }

  @Override
  public void setDiskSpaceAvailable(final boolean b) {}

  @Override
  public boolean shouldProcess() {
    return partitionProcessingState.shouldProcess();
  }

  @Override
  public void pauseProcessing() throws IOException {
    partitionProcessingState.pauseProcessing();
  }

  @Override
  public void resumeProcessing() throws IOException {
    partitionProcessingState.resumeProcessing();
  }

  @Override
  public boolean shouldExport() {
    return !partitionProcessingState.isExportingPaused();
  }

  @Override
  public boolean pauseExporting() throws IOException {
    return partitionProcessingState.pauseExporting();
  }

  @Override
  public boolean resumeExporting() throws IOException {
    return partitionProcessingState.resumeExporting();
  }
}
