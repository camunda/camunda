/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.monitoring.HealthTreeMetrics;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandReceiverActor;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderService;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.db.SnapshotCopy;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.ComponentTreeListener;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
  private final List<PartitionRaftListener> partitionRaftListeners;
  private final int partitionCount;
  private final ClusterCommunicationService clusterCommunicationService;
  private final PartitionMessagingService messagingService;
  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;
  private final RaftPartition raftPartition;
  private final TypedRecordProcessorsFactory typedRecordProcessorsFactory;
  private final CommandApiService commandApiService;
  private final PersistedSnapshotStore persistedSnapshotStore;
  private final PartitionId partitionId;
  private final int maxFragmentSize;
  private final ExporterRepository exporterRepository;
  private final PartitionProcessingState partitionProcessingState;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final SnapshotCopy snapshotCopy;
  private final StateController stateController;
  private DynamicPartitionConfig dynamicPartitionConfig;
  private StreamProcessor streamProcessor;
  private LogStream logStream;
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
  private InterPartitionCommandReceiverActor interPartitionCommandReceiver;
  private InterPartitionCommandSenderService interPartitionCommandSender;
  private final AtomixServerTransport gatewayBrokerTransport;
  private BackupApiRequestHandler backupApiRequestHandler;
  private BackupManager backupManager;
  private CheckpointRecordsProcessor checkpointRecordsProcessor;
  private final TopologyManager topologyManager;
  private BackupStore backupStore;
  private AdminApiRequestHandler adminApiService;
  private PartitionAdminAccess adminAccess;
  private ControllableStreamClock clock;
  private final HealthTreeMetrics healthGraphMetrics;
  private final BrokerHealthCheckService brokerHealthCheckService;
  private final SecurityConfiguration securityConfig;
  private final MeterRegistry startupMeterRegistry;
  private MeterRegistry transitionMeterRegistry;
  private volatile boolean migrationsPerformed = false;
  private final SnapshotApiRequestHandler snapshotApiRequestHandler;

  public PartitionStartupAndTransitionContextImpl(
      final int nodeId,
      final int partitionCount,
      final ClusterCommunicationService clusterCommunicationService,
      final RaftPartition raftPartition,
      final List<PartitionListener> partitionListeners,
      final List<PartitionRaftListener> partitionRaftListeners,
      final PartitionMessagingService partitionCommunicationService,
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final CommandApiService commandApiService,
      final PersistedSnapshotStore persistedSnapshotStore,
      final SnapshotCopy snapshotCopy,
      final SnapshotApiRequestHandler snapshotApiRequestHandler,
      final StateController stateController,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory,
      final ExporterRepository exporterRepository,
      final PartitionProcessingState partitionProcessingState,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final AtomixServerTransport gatewayBrokerTransport,
      final TopologyManager topologyManager,
      final BrokerHealthCheckService brokerHealthCheckService,
      final SecurityConfiguration securityConfig,
      final MeterRegistry startupMeterRegistry) {
    this.nodeId = nodeId;
    this.partitionCount = partitionCount;
    this.clusterCommunicationService = clusterCommunicationService;
    this.raftPartition = raftPartition;
    messagingService = partitionCommunicationService;
    this.brokerCfg = brokerCfg;
    this.snapshotApiRequestHandler = snapshotApiRequestHandler;
    this.stateController = stateController;
    this.snapshotCopy = snapshotCopy;
    this.typedRecordProcessorsFactory = typedRecordProcessorsFactory;
    this.commandApiService = commandApiService;
    this.persistedSnapshotStore = persistedSnapshotStore;
    this.partitionListeners = Collections.unmodifiableList(partitionListeners);
    this.partitionRaftListeners = Collections.unmodifiableList(partitionRaftListeners);
    partitionId = raftPartition.id();
    this.actorSchedulingService = actorSchedulingService;
    maxFragmentSize = (int) brokerCfg.getNetwork().getMaxMessageSizeInBytes();
    this.exporterRepository = exporterRepository;
    this.partitionProcessingState = partitionProcessingState;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.gatewayBrokerTransport = gatewayBrokerTransport;
    this.topologyManager = topologyManager;
    this.brokerHealthCheckService = brokerHealthCheckService;
    this.securityConfig = securityConfig;
    this.startupMeterRegistry = startupMeterRegistry;
    healthGraphMetrics = new HealthTreeMetrics(startupMeterRegistry);
  }

  public PartitionAdminControl getPartitionAdminControl() {
    return new PartitionAdminControlImpl(
        () -> getPartitionContext().getStreamProcessor(),
        () -> getPartitionContext().getExporterDirector(),
        () -> snapshotDirector,
        () -> partitionProcessingState,
        () -> zeebeDb,
        () -> logStream);
  }

  @Override
  public int getPartitionId() {
    return partitionId.id();
  }

  @Override
  public PartitionId getFullPartitionId() {
    return partitionId;
  }

  @Override
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  @Override
  public void notifyListenersOfBecameRaftLeader(final long newTerm) {
    partitionRaftListeners.forEach(l -> l.onBecameRaftLeader(getPartitionId(), newTerm));
  }

  @Override
  public void notifyListenersOfBecameRaftFollower(final long newTerm) {
    partitionRaftListeners.forEach(l -> l.onBecameRaftFollower(getPartitionId(), newTerm));
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
  public ClusterCommunicationService getClusterCommunicationService() {
    return clusterCommunicationService;
  }

  @Override
  public InterPartitionCommandReceiverActor getPartitionCommandReceiver() {
    return interPartitionCommandReceiver;
  }

  @Override
  public void setPartitionCommandReceiver(final InterPartitionCommandReceiverActor receiver) {
    interPartitionCommandReceiver = receiver;
  }

  @Override
  public InterPartitionCommandSenderService getPartitionCommandSender() {
    return interPartitionCommandSender;
  }

  @Override
  public void setPartitionCommandSender(final InterPartitionCommandSenderService sender) {
    interPartitionCommandSender = sender;
  }

  @Override
  public ExporterPhase getExporterPhase() {
    return partitionProcessingState.getExporterPhase();
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
  public SecurityConfiguration getSecurityConfig() {
    return securityConfig;
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
  public DiskSpaceUsageMonitor getDiskSpaceUsageMonitor() {
    return diskSpaceUsageMonitor;
  }

  @Override
  public AtomixServerTransport getGatewayBrokerTransport() {
    return gatewayBrokerTransport;
  }

  @Override
  public BackupApiRequestHandler getBackupApiRequestHandler() {
    return backupApiRequestHandler;
  }

  @Override
  public void setBackupApiRequestHandler(final BackupApiRequestHandler backupApiRequestHandler) {
    this.backupApiRequestHandler = backupApiRequestHandler;
  }

  @Override
  public BackupManager getBackupManager() {
    return backupManager;
  }

  @Override
  public void setBackupManager(final BackupManager backupManager) {
    this.backupManager = backupManager;
  }

  @Override
  public CheckpointRecordsProcessor getCheckpointProcessor() {
    return checkpointRecordsProcessor;
  }

  @Override
  public void setCheckpointProcessor(final CheckpointRecordsProcessor checkpointRecordsProcessor) {
    this.checkpointRecordsProcessor = checkpointRecordsProcessor;
  }

  @Override
  public BackupStore getBackupStore() {
    return backupStore;
  }

  @Override
  public void setBackupStore(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  @Override
  public MeterRegistry getPartitionStartupMeterRegistry() {
    return startupMeterRegistry;
  }

  @Override
  public MeterRegistry getPartitionTransitionMeterRegistry() {
    return transitionMeterRegistry;
  }

  @Override
  public void setPartitionTransitionMeterRegistry(final MeterRegistry transitionMeterRegistry) {
    this.transitionMeterRegistry = transitionMeterRegistry;
  }

  @Override
  public void markMigrationsDone() {
    migrationsPerformed = true;
  }

  @Override
  public boolean areMigrationsPerformed() {
    return migrationsPerformed;
  }

  @Override
  public ComponentTreeListener getComponentTreeListener() {
    return healthGraphMetrics;
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
  public TopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public AdminApiRequestHandler getAdminApiService() {
    return adminApiService;
  }

  @Override
  public void setAdminApiRequestHandler(final AdminApiRequestHandler handler) {
    adminApiService = handler;
  }

  @Override
  public PartitionAdminAccess getAdminAccess() {
    return adminAccess;
  }

  @Override
  public void setAdminAccess(final PartitionAdminAccess adminAccess) {
    this.adminAccess = adminAccess;
  }

  @Override
  public DynamicPartitionConfig getDynamicPartitionConfig() {
    return dynamicPartitionConfig;
  }

  @Override
  public void setDynamicPartitionConfig(final DynamicPartitionConfig partitionConfig) {
    dynamicPartitionConfig = partitionConfig;
  }

  @Override
  public SnapshotApiRequestHandler getSnapshotApiRequestHandler() {
    return snapshotApiRequestHandler;
  }

  @Override
  public ControllableStreamClock getStreamClock() {
    return clock;
  }

  @Override
  public void setStreamClock(final ControllableStreamClock clock) {
    this.clock = clock;
  }

  @Override
  public int getPartitionCount() {
    return partitionCount;
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
  public SnapshotCopy snapshotCopy() {
    return snapshotCopy;
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
  public PersistedSnapshotStore getPersistedSnapshotStore() {
    return persistedSnapshotStore;
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
  public BrokerHealthCheckService brokerHealthCheckService() {
    return brokerHealthCheckService;
  }

  @Override
  public void setZeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  @Override
  public CommandApiService getCommandApiService() {
    return commandApiService;
  }

  @Override
  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorsFactory::createTypedStreamProcessor;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  @Override
  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
  }

  public ExporterRepository getExporterRepository() {
    return exporterRepository;
  }

  @Override
  public String toString() {
    return "PartitionStartupAndTransitionContextImpl{"
        + "partitionId="
        + partitionId
        + ", currentTerm="
        + currentTerm
        + ", currentRole="
        + currentRole
        + '}';
  }
}
