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
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.logstreams.LogDeletionService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class encapsulating all the information about a partition that are needed during bootstrap and
 * transition to the role of the partition
 */
@Deprecated // will be split up according to interfaces
public class PartitionStartupAndTransitionContextImpl
    implements PartitionContext, PartitionStartupContext, PartitionTransitionContext {

  private final int nodeId;
  private final List<PartitionListener> partitionListeners;
  private final PartitionMessagingService messagingService;
  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;

  private final RaftPartition raftPartition;
  private final TypedRecordProcessorsFactory typedRecordProcessorsFactory;
  private final Supplier<CommandResponseWriter> commandResponseWriterSupplier;
  private final Supplier<Consumer<TypedRecord<?>>> onProcessedListenerSupplier;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ReceivableSnapshotStore receivableSnapshotStore;
  private final Integer partitionId;
  private final int maxFragmentSize;
  private final ExporterRepository exporterRepository;
  private final PartitionProcessingState partitionProcessingState;
  private final StateController stateController;

  private StreamProcessor streamProcessor;
  private LogStream logStream;
  private LogDeletionService logDeletionService;
  private AsyncSnapshotDirector snapshotDirector;
  private HealthMonitor criticalComponentsHealthMonitor;
  private ZeebeDb zeebeDb;
  private ActorControl actorControl;
  private ScheduledTimer metricsTimer;
  private ExporterDirector exporterDirector;
  private AtomixLogStorage logStorage;
  private QueryService queryService;

  private long currentTerm;
  private Role currentRole;
  private ConcurrencyControl concurrencyControl;

  public PartitionStartupAndTransitionContextImpl(
      final int nodeId,
      final RaftPartition raftPartition,
      final List<PartitionListener> partitionListeners,
      final PartitionMessagingService messagingService,
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final Supplier<CommandResponseWriter> commandResponseWriterSupplier,
      final Supplier<Consumer<TypedRecord<?>>> onProcessedListenerSupplier,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final ReceivableSnapshotStore receivableSnapshotStore,
      final StateController stateController,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory,
      final ExporterRepository exporterRepository,
      final PartitionProcessingState partitionProcessingState) {
    this.nodeId = nodeId;
    this.raftPartition = raftPartition;
    this.messagingService = messagingService;
    this.brokerCfg = brokerCfg;
    this.stateController = stateController;
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

  public PartitionAdminControl getPartitionAdminControl() {
    return new PartitionAdminControlImpl(
        () -> getPartitionContext().getStreamProcessor(),
        () -> getPartitionContext().getExporterDirector(),
        () -> snapshotDirector,
        () -> partitionProcessingState);
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
  public List<ActorFuture<Void>> notifyListenersOfBecomingLeader(final long newTerm) {
    return partitionListeners.stream()
        .map(l -> l.onBecomingLeader(getPartitionId(), newTerm, getLogStream(), getQueryService()))
        .collect(Collectors.toList());
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm) {
    return partitionListeners.stream()
        .map(l -> l.onBecomingFollower(getPartitionId(), newTerm))
        .collect(Collectors.toList());
  }

  @Override
  public void notifyListenersOfBecomingInactive() {
    partitionListeners.forEach(l -> l.onBecomingInactive(getPartitionId(), getCurrentTerm()));
  }

  @Override
  public Role getCurrentRole() {
    return currentRole;
  }

  @Override
  public long getCurrentTerm() {
    return currentTerm;
  }

  @Override
  public HealthMonitor getComponentHealthMonitor() {
    return criticalComponentsHealthMonitor;
  }

  public void setComponentHealthMonitor(final HealthMonitor criticalComponentsHealthMonitor) {
    this.criticalComponentsHealthMonitor = criticalComponentsHealthMonitor;
  }

  @Override
  public StreamProcessor getStreamProcessor() {
    return streamProcessor;
  }

  @Override
  public void setStreamProcessor(final StreamProcessor streamProcessor) {
    this.streamProcessor = streamProcessor;
  }

  @Override
  public ExporterDirector getExporterDirector() {
    return exporterDirector;
  }

  @Override
  public void setExporterDirector(final ExporterDirector exporterDirector) {
    this.exporterDirector = exporterDirector;
  }

  @Override
  public PartitionMessagingService getMessagingService() {
    return messagingService;
  }

  @Override
  public boolean shouldExport() {
    return !partitionProcessingState.isExportingPaused();
  }

  @Override
  public Collection<ExporterDescriptor> getExportedDescriptors() {
    return getExporterRepository().getExporters().values();
  }

  @Override
  public AtomixLogStorage getLogStorage() {
    return logStorage;
  }

  @Override
  public void setLogStorage(final AtomixLogStorage logStorage) {
    this.logStorage = logStorage;
  }

  @Override
  public int getMaxFragmentSize() {
    return maxFragmentSize;
  }

  @Override
  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  @Override
  public QueryService getQueryService() {
    return queryService;
  }

  @Override
  public void setQueryService(final QueryService queryService) {
    this.queryService = queryService;
  }

  @Override
  public boolean shouldProcess() {
    return partitionProcessingState.shouldProcess();
  }

  @Override
  public void setDiskSpaceAvailable(final boolean diskSpaceAvailable) {
    partitionProcessingState.setDiskSpaceAvailable(diskSpaceAvailable);
  }

  @Override
  public void setCurrentTerm(final long currentTerm) {
    this.currentTerm = currentTerm;
  }

  @Override
  public void setCurrentRole(final Role currentRole) {
    this.currentRole = currentRole;
  }

  @Override
  public LogStream getLogStream() {
    return logStream;
  }

  @Override
  public void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public AsyncSnapshotDirector getSnapshotDirector() {
    return snapshotDirector;
  }

  @Override
  public void setSnapshotDirector(final AsyncSnapshotDirector snapshotDirector) {
    this.snapshotDirector = snapshotDirector;
  }

  @Override
  public StateController getStateController() {
    return stateController;
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }

  @Override
  public PartitionContext getPartitionContext() {
    return this;
  }

  @Override
  public int getNodeId() {
    return nodeId;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
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
  public ActorControl getActorControl() {
    return actorControl;
  }

  @Override
  public void setActorControl(final ActorControl actorControl) {
    this.actorControl = actorControl;
  }

  @Override
  public LogDeletionService getLogDeletionService() {
    return logDeletionService;
  }

  @Override
  public void setLogDeletionService(final LogDeletionService logDeletionService) {
    this.logDeletionService = logDeletionService;
  }

  @Override
  public ScheduledTimer getMetricsTimer() {
    return metricsTimer;
  }

  @Override
  public void setMetricsTimer(final ScheduledTimer metricsTimer) {
    this.metricsTimer = metricsTimer;
  }

  @Override
  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  @Override
  public PartitionStartupAndTransitionContextImpl createTransitionContext() {
    return this;
  }

  @Override
  public void setZeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  @Override
  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriterSupplier.get();
  }

  @Override
  public Consumer<TypedRecord<?>> getOnProcessedListener() {
    return onProcessedListenerSupplier.get();
  }

  @Override
  public TypedRecordProcessorFactory getStreamProcessorFactory() {
    return typedRecordProcessorsFactory::createTypedStreamProcessor;
  }

  public ExporterRepository getExporterRepository() {
    return exporterRepository;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  @Override
  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
  }
}
