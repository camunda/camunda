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
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.util.List;

/**
 * Class encapsulating all the information about a partition that are needed during transition to
 * the role of the partition
 *
 * <p><strong>Note:</strong> Currently this class implements {@code PartitionConcept}. This is for
 * legacy reasons to keep the change set small. In the future the transition should be the process
 * by which the partition context is created.
 */
public class PartitionTransitionContext {

  private final int nodeId;
  private final RaftPartition raftPartition;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final Integer partitionId;
  private final PartitionProcessingState partitionProcessingState;

  private final ExporterDirector exporterDirector;
  private final AsyncSnapshotDirector snapshotDirector;
  private final StateControllerImpl stateController;
  private final StreamProcessor streamProcessor;
  private final List<PartitionListener> partitionListeners;
  private final LogStream logStream;

  public PartitionTransitionContext(final PartitionBootstrapContext bootstrapContext) {
    nodeId = bootstrapContext.getNodeId();
    raftPartition = bootstrapContext.getRaftPartition();

    constructableSnapshotStore = bootstrapContext.getConstructableSnapshotStore();

    partitionId = raftPartition.id().id();
    partitionProcessingState = bootstrapContext.getPartitionProcessingState();
    exporterDirector = bootstrapContext.getExporterDirector();
    snapshotDirector = bootstrapContext.getSnapshotDirector();
    stateController = bootstrapContext.getStateController();
    streamProcessor = bootstrapContext.getStreamProcessor();
    partitionListeners = bootstrapContext.getPartitionListeners();
    logStream = bootstrapContext.getLogStream();
  }

  public ExporterDirector getExporterDirector() {
    return exporterDirector;
  }

  public AsyncSnapshotDirector getSnapshotDirector() {
    return snapshotDirector;
  }

  public StateControllerImpl getSnapshotController() {
    return stateController;
  }

  public StreamProcessor getStreamProcessor() {
    return streamProcessor;
  }

  public int getNodeId() {
    return nodeId;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ConstructableSnapshotStore getConstructableSnapshotStore() {
    return constructableSnapshotStore;
  }

  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  public Role getCurrentRole() {
    return raftPartition.getRole();
  }

  public PartitionContext toPartitionContext() {
    return new PartitionContextImpl(this, partitionProcessingState);
  }

  public boolean shouldExport() {
    return !partitionProcessingState.isExportingPaused();
  }

  public boolean shouldProcess() {
    return partitionProcessingState.shouldProcess();
  }

  public List<PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }

  public LogStream getLogStream() {
    return logStream;
  }
}
