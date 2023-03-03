/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandReceiverActor;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TestPartitionTransitionContext implements PartitionTransitionContext {

  private RaftPartition raftPartition;
  private Role currentRole;
  private long currentTerm;
  private HealthMonitor healthMonitor;
  private TypedRecordProcessorFactory typedRecordProcessorFactory;
  private ExporterDirector exporterDirector;
  private LogStream logStream;
  private StreamProcessor streamProcessor;
  private ActorSchedulingService actorSchedulingService;
  private ZeebeDb zeebeDB;
  private StateController stateController;
  private ExporterRepository exporterRepository;
  private AtomixLogStorage logStorage;
  private BrokerCfg brokerCfg;
  private AsyncSnapshotDirector snapshotDirector;
  private QueryService queryService;
  private ConcurrencyControl concurrencyControl;
  private InterPartitionCommandReceiverActor interPartitionCommandReceiver;
  private DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private AtomixServerTransport gatewayBrokerTransport;
  private BackupApiRequestHandler backupApiRequestHandler;
  private BackupManager backupManager;
  private CheckpointRecordsProcessor checkpointRecordsProcessor;
  private BackupStore backupStore;
  private final GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer =
      GatewayStreamer.noop();

  @Override
  public int getPartitionId() {
    return 1;
  }

  @Override
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingLeader(final long newTerm) {
    return List.of(TestActorFuture.completedFuture(null));
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm) {
    return List.of(TestActorFuture.completedFuture(null));
  }

  @Override
  public void notifyListenersOfBecomingInactive() {}

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
    return healthMonitor;
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
  public boolean shouldProcess() {
    return true;
  }

  @Override
  public void setDiskSpaceAvailable(final boolean b) {}

  @Override
  public TopologyManager getTopologyManager() {
    return null;
  }

  @Override
  public void setExporterDirector(final ExporterDirector exporterDirector) {
    this.exporterDirector = exporterDirector;
  }

  @Override
  public PartitionMessagingService getMessagingService() {
    return null;
  }

  @Override
  public ClusterCommunicationService getClusterCommunicationService() {
    return null;
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
    return null;
  }

  @Override
  public void setPartitionCommandSender(final InterPartitionCommandSenderService sender) {}

  @Override
  public boolean shouldExport() {
    return true;
  }

  @Override
  public Collection<ExporterDescriptor> getExportedDescriptors() {
    return Set.of();
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
    return 1;
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
  public GatewayStreamer<JobActivationProperties, ActivatedJob> getJobStreamer() {
    return jobStreamer;
  }

  public void setGatewayBrokerTransport(final AtomixServerTransport gatewayBrokerTransport) {
    this.gatewayBrokerTransport = gatewayBrokerTransport;
  }

  public void setDiskSpaceUsageMonitor(final DiskSpaceUsageMonitor diskSpaceUsageMonitor) {
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
  }

  public void setBrokerCfg(final BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Override
  public void setStreamProcessor(final StreamProcessor streamProcessor) {
    this.streamProcessor = streamProcessor;
  }

  public void setComponentHealthMonitor(final HealthMonitor healthMonitor) {
    this.healthMonitor = healthMonitor;
  }

  @Override
  public void setCurrentTerm(final long term) {
    currentTerm = term;
  }

  @Override
  public void setCurrentRole(final Role role) {
    currentRole = role;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  public void setActorSchedulingService(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
  }

  @Override
  public ZeebeDb getZeebeDb() {
    return zeebeDB;
  }

  @Override
  public void setZeebeDb(final ZeebeDb zeebeDb) {
    zeebeDB = zeebeDb;
  }

  @Override
  public CommandResponseWriter getCommandResponseWriter() {
    return null;
  }

  @Override
  public Consumer<TypedRecord<?>> getOnProcessedListener() {
    return null;
  }

  @Override
  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorFactory;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  @Override
  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
  }

  public void setTypedRecordProcessorFactory(
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
  }

  public void setRaftPartition(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
  }

  public void setExporterRepository(final ExporterRepository exporterRepository) {
    this.exporterRepository = exporterRepository;
  }

  @Override
  public int getNodeId() {
    return 0;
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
  public PersistedSnapshotStore getPersistedSnapshotStore() {
    return null;
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return null;
  }

  @Override
  public PartitionContext getPartitionContext() {
    return null;
  }

  public void setStateController(final StateController stateController) {
    this.stateController = stateController;
  }
}
