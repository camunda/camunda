/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PartitionBootstrapContextImpl implements PartitionBootstrapContext {

  private final int partitionId;
  private final int nodeId;
  private final BrokerCfg brokerCfg;
  private final RaftPartition raftPartition;
  private final PartitionMessagingService messagingService;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ReceivableSnapshotStore receivableSnapshotStore;
  private final ActorSchedulingService actorSchedulingService;
  private final Supplier<CommandResponseWriter> commandResponseWriter;
  private final Supplier<Consumer<TypedRecord>> onProcessedListener;
  private final TypedRecordProcessorsFactory typedRecordProcessorsFactory;
  private final ExporterRepository exporterRepository;
  private final PartitionProcessingState partitionProcessingState;
  private final HealthMonitor healthMonitor;
  private final List<PartitionListener> partitionListeners;

  // constructed during bootstrapping
  private SnapshotReplication snapshotReplication;
  private StateControllerImpl stateController;
  private LogDeletionService logDeletionService;
  private LogStream logStream;

  private ZeebeDb zeebeDb;
  private StreamProcessor streamProcessor;
  private AsyncSnapshotDirector snapshotDirector;
  private ActorControl actorControl;
  private ScheduledTimer metricsTimer;
  private ExporterDirector exporterDirector;

  public PartitionBootstrapContextImpl(
      final int nodeId,
      final BrokerCfg brokerCfg,
      final RaftPartition raftPartition,
      final PartitionMessagingService messagingService,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final ReceivableSnapshotStore receivableSnapshotStore,
      final ActorSchedulingService actorSchedulingService,
      final Supplier<CommandResponseWriter> commandResponseWriter,
      final Supplier<Consumer<TypedRecord>> onProcessedListener,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory,
      final ExporterRepository exporterRepository,
      final PartitionProcessingState partitionProcessingState,
      final ActorControl actorControl,
      final HealthMonitor healthMonitor,
      final List<PartitionListener> partitionListeners) {
    partitionId = raftPartition.id().id();
    this.nodeId = nodeId;
    this.brokerCfg = brokerCfg;
    this.raftPartition = raftPartition;
    this.messagingService = messagingService;
    this.constructableSnapshotStore = constructableSnapshotStore;
    this.receivableSnapshotStore = receivableSnapshotStore;
    this.actorSchedulingService = actorSchedulingService;
    this.commandResponseWriter = commandResponseWriter;
    this.onProcessedListener = onProcessedListener;
    this.typedRecordProcessorsFactory = typedRecordProcessorsFactory;
    this.exporterRepository = exporterRepository;
    this.partitionProcessingState = partitionProcessingState;
    this.actorControl = actorControl;
    this.healthMonitor = healthMonitor;
    this.partitionListeners = partitionListeners;
  }

  @Override
  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  @Override
  public PartitionMessagingService getMessagingService() {
    return messagingService;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public int getNodeId() {
    return nodeId;
  }

  @Override
  public void setSnapshotReplication(final SnapshotReplication replication) {
    snapshotReplication = replication;
  }

  @Override
  public SnapshotReplication getSnapshotReplication() {
    return snapshotReplication;
  }

  @Override
  public void setStateController(final StateControllerImpl stateController) {
    this.stateController = stateController;
  }

  @Override
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  @Override
  public ConstructableSnapshotStore getConstructableSnapshotStore() {
    return constructableSnapshotStore;
  }

  @Override
  public ReceivableSnapshotStore getReceivableSnapshotStore() {
    return receivableSnapshotStore;
  }

  @Override
  public StateControllerImpl getStateController() {
    return stateController;
  }

  @Override
  public void setLogDeletionService(final LogDeletionService deletionService) {
    logDeletionService = deletionService;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  @Override
  public LogDeletionService getLogDeletionService() {
    return logDeletionService;
  }

  @Override
  public void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public LogStream getLogStream() {
    return logStream;
  }

  @Override
  public void setZeebeDb(final ZeebeDb db) {
    zeebeDb = db;
  }

  @Override
  public StreamProcessor getStreamProcessor() {
    return streamProcessor;
  }

  @Override
  public void setStreamProcessor(final StreamProcessor o) {
    streamProcessor = o;
  }

  @Override
  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  @Override
  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter.get();
  }

  @Override
  public Consumer<TypedRecord> getOnProcessedListener() {
    return onProcessedListener.get();
  }

  @Override
  public TypedRecordProcessorsFactory getTypedRecordProcessorsFactory() {
    return typedRecordProcessorsFactory;
  }

  @Override
  public void setSnapshotDirector(final AsyncSnapshotDirector director) {
    snapshotDirector = director;
  }

  @Override
  public AsyncSnapshotDirector getSnapshotDirector() {
    return snapshotDirector;
  }

  @Override
  public ActorControl getActorControl() {
    return actorControl;
  }

  @Override
  public void setActorControl(final ActorControl actorControl) {
    this.actorControl = actorControl;
  }

  @Override
  public void setMetricsTimer(final ScheduledTimer metricsTimer) {
    this.metricsTimer = metricsTimer;
  }

  @Override
  public ScheduledTimer getMetricsTimer() {
    return metricsTimer;
  }

  @Override
  public void setExporterDirector(final ExporterDirector director) {
    exporterDirector = director;
  }

  @Override
  public ExporterRepository getExporterRepository() {
    return exporterRepository;
  }

  @Override
  public ExporterDirector getExporterDirector() {
    return exporterDirector;
  }

  @Override
  public PartitionProcessingState getPartitionProcessingState() {
    return partitionProcessingState;
  }

  @Override
  public HealthMonitor getComponentHealthMonitor() {
    return healthMonitor;
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }

  @Override
  public PartitionTransitionContext toTransitionContext() {
    return new PartitionTransitionContext(this);
  }
}
