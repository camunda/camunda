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
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.logstreams.LogDeletionService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class encapsulating all the information about a partition that are needed during transition to
 * the role of the partition
 *
 * <p><strong>Note:</strong> Currently this class implements {@code PartitionConcept}. This is for
 * legacy reasons to keep the change set small. In the future the transition should be the process
 * by which the partition context is created.
 */
public class PartitionTransitionContextImpl implements PartitionContext {

  private final int nodeId;
  private final List<PartitionListener> partitionListeners;
  private final PartitionMessagingService messagingService;
  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;

  private final RaftPartition raftPartition;
  private final TypedRecordProcessorsFactory typedRecordProcessorsFactory;
  private final Supplier<CommandResponseWriter> commandResponseWriterSupplier;
  private final Supplier<Consumer<TypedRecord>> onProcessedListenerSupplier;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ReceivableSnapshotStore receivableSnapshotStore;
  private final Integer partitionId;
  private final int maxFragmentSize;
  private final ExporterRepository exporterRepository;
  private final PartitionProcessingState partitionProcessingState;

  private StreamProcessor streamProcessor;
  private LogStream logStream;
  private SnapshotReplication snapshotReplication;
  private StateControllerImpl stateController;
  private LogDeletionService logDeletionService;
  private AsyncSnapshotDirector snapshotDirector;
  private HealthMonitor criticalComponentsHealthMonitor;
  private ZeebeDb zeebeDb;
  private ActorControl actor;
  private ScheduledTimer metricsTimer;
  private ExporterDirector exporterDirector;

  private long currentTerm;
  private Role currentRole;

  public PartitionTransitionContextImpl(
      final int nodeId,
      final RaftPartition raftPartition,
      final List<PartitionListener> partitionListeners,
      final PartitionMessagingService messagingService,
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final Supplier<CommandResponseWriter> commandResponseWriterSupplier,
      final Supplier<Consumer<TypedRecord>> onProcessedListenerSupplier,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final ReceivableSnapshotStore receivableSnapshotStore,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory,
      final ExporterRepository exporterRepository,
      final PartitionProcessingState partitionProcessingState) {
    this.nodeId = nodeId;
    this.raftPartition = raftPartition;
    this.messagingService = messagingService;
    this.brokerCfg = brokerCfg;
    this.typedRecordProcessorsFactory = typedRecordProcessorsFactory;
    this.onProcessedListenerSupplier = onProcessedListenerSupplier;
    this.commandResponseWriterSupplier = commandResponseWriterSupplier;
    this.constructableSnapshotStore = constructableSnapshotStore;
    this.receivableSnapshotStore = receivableSnapshotStore;
    this.partitionListeners = Collections.unmodifiableList(partitionListeners);
    partitionId = raftPartition.id().id();
    this.actorSchedulingService = actorSchedulingService;
    maxFragmentSize = (int) brokerCfg.getNetwork().getMaxMessageSizeInBytes();
    this.exporterRepository = exporterRepository;
    this.partitionProcessingState = partitionProcessingState;
  }

  @Override
  public ExporterDirector getExporterDirector() {
    return exporterDirector;
  }

  public void setExporterDirector(final ExporterDirector exporterDirector) {
    this.exporterDirector = exporterDirector;
  }

  public ScheduledTimer getMetricsTimer() {
    return metricsTimer;
  }

  public void setMetricsTimer(final ScheduledTimer metricsTimer) {
    this.metricsTimer = metricsTimer;
  }

  public ActorControl getActor() {
    return actor;
  }

  public void setActor(final ActorControl actor) {
    this.actor = actor;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public void setZeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  @Override
  public HealthMonitor getComponentHealthMonitor() {
    return criticalComponentsHealthMonitor;
  }

  public void setComponentHealthMonitor(final HealthMonitor criticalComponentsHealthMonitor) {
    this.criticalComponentsHealthMonitor = criticalComponentsHealthMonitor;
  }

  public AsyncSnapshotDirector getSnapshotDirector() {
    return snapshotDirector;
  }

  public void setSnapshotDirector(final AsyncSnapshotDirector snapshotDirector) {
    this.snapshotDirector = snapshotDirector;
  }

  public LogDeletionService getLogDeletionService() {
    return logDeletionService;
  }

  public void setLogDeletionService(final LogDeletionService logDeletionService) {
    this.logDeletionService = logDeletionService;
  }

  public StateControllerImpl getSnapshotController() {
    return stateController;
  }

  public void setSnapshotController(final StateControllerImpl controller) {
    stateController = controller;
  }

  public SnapshotReplication getSnapshotReplication() {
    return snapshotReplication;
  }

  public void setSnapshotReplication(final SnapshotReplication snapshotReplication) {
    this.snapshotReplication = snapshotReplication;
  }

  @Override
  public StreamProcessor getStreamProcessor() {
    return streamProcessor;
  }

  public void setStreamProcessor(final StreamProcessor streamProcessor) {
    this.streamProcessor = streamProcessor;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }

  public int getNodeId() {
    return nodeId;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  public PartitionMessagingService getMessagingService() {
    return messagingService;
  }

  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  public ConstructableSnapshotStore getConstructableSnapshotStore() {
    return constructableSnapshotStore;
  }

  public ReceivableSnapshotStore getReceivableSnapshotStore() {
    return receivableSnapshotStore;
  }

  @Override
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  public TypedRecordProcessorsFactory getTypedRecordProcessorsFactory() {
    return typedRecordProcessorsFactory;
  }

  public int getMaxFragmentSize() {
    return maxFragmentSize;
  }

  public ExporterRepository getExporterRepository() {
    return exporterRepository;
  }

  @Override
  public void setDiskSpaceAvailable(final boolean diskSpaceAvailable) {
    partitionProcessingState.setDiskSpaceAvailable(diskSpaceAvailable);
  }

  @Override
  public boolean shouldProcess() {
    return partitionProcessingState.shouldProcess();
  }

  @Override
  public boolean shouldExport() {
    return !partitionProcessingState.isExportingPaused();
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
  public boolean pauseExporting() throws IOException {
    return partitionProcessingState.pauseExporting();
  }

  @Override
  public boolean resumeExporting() throws IOException {
    return partitionProcessingState.resumeExporting();
  }

  @Override
  public long getCurrentTerm() {
    return currentTerm;
  }

  public void setCurrentTerm(final long currentTerm) {
    this.currentTerm = currentTerm;
  }

  @Override
  public Role getCurrentRole() {
    return currentRole;
  }

  public void setCurrentRole(final Role currentRole) {
    this.currentRole = currentRole;
  }

  public PartitionContext toPartitionContext() {
    return this;
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm) {
    return partitionListeners.stream()
        .map(l -> l.onBecomingFollower(getPartitionId(), newTerm))
        .collect(Collectors.toList());
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingLeader(final long newTerm) {
    return partitionListeners.stream()
        .map(l -> l.onBecomingLeader(getPartitionId(), newTerm, getLogStream()))
        .collect(Collectors.toList());
  }

  @Override
  public void notifyListenersOfBecomingInactive() {
    partitionListeners.forEach(l -> l.onBecomingInactive(getPartitionId(), getCurrentTerm()));
  }

  @Override
  public void triggerSnapshot() {
    if (getSnapshotDirector() != null) {
      getSnapshotDirector().forceSnapshot();
    }
  }

  public Consumer<TypedRecord> getOnProcessedListener() {
    return onProcessedListenerSupplier.get();
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriterSupplier.get();
  }
}
