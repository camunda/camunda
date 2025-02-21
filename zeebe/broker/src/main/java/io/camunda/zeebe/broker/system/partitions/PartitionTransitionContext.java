/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandReceiverActor;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;

public interface PartitionTransitionContext extends PartitionContext {

  int getNodeId();

  LogStream getLogStream();

  void setLogStream(LogStream logStream);

  AsyncSnapshotDirector getSnapshotDirector();

  void setSnapshotDirector(AsyncSnapshotDirector snapshotDirector);

  StateController getStateController();

  PersistedSnapshotStore getPersistedSnapshotStore();

  List<PartitionListener> getPartitionListeners();

  PartitionContext getPartitionContext();

  void setStreamProcessor(StreamProcessor streamProcessor);

  void setCurrentTerm(long term);

  void setCurrentRole(Role role);

  ActorSchedulingService getActorSchedulingService();

  ZeebeDb getZeebeDb();

  void setZeebeDb(ZeebeDb zeebeDb);

  CommandApiService getCommandApiService();

  TypedRecordProcessorFactory getTypedRecordProcessorFactory();

  ConcurrencyControl getConcurrencyControl();

  void setConcurrencyControl(ConcurrencyControl concurrencyControl);

  void setExporterDirector(ExporterDirector exporterDirector);

  PartitionMessagingService getMessagingService();

  ClusterCommunicationService getClusterCommunicationService();

  InterPartitionCommandReceiverActor getPartitionCommandReceiver();

  void setPartitionCommandReceiver(InterPartitionCommandReceiverActor receiver);

  InterPartitionCommandSenderService getPartitionCommandSender();

  void setPartitionCommandSender(InterPartitionCommandSenderService sender);

  ExporterPhase getExporterPhase();

  Collection<ExporterDescriptor> getExportedDescriptors();

  AtomixLogStorage getLogStorage();

  void setLogStorage(AtomixLogStorage logStorage);

  int getMaxFragmentSize();

  BrokerCfg getBrokerCfg();

  QueryService getQueryService();

  void setQueryService(QueryService queryService);

  DiskSpaceUsageMonitor getDiskSpaceUsageMonitor();

  AtomixServerTransport getGatewayBrokerTransport();

  BackupApiRequestHandler getBackupApiRequestHandler();

  void setBackupApiRequestHandler(BackupApiRequestHandler backupApiRequestHandler);

  BackupManager getBackupManager();

  void setBackupManager(BackupManager backupManager);

  CheckpointRecordsProcessor getCheckpointProcessor();

  void setCheckpointProcessor(CheckpointRecordsProcessor checkpointRecordsProcessor);

  BackupStore getBackupStore();

  void setBackupStore(BackupStore backupStore);

  /**
   * Returns a meter registry which already has some common tags for the partition (so you can omit
   * adding it manually to your metrics), and which is created when the partition is bootstrapped or
   * joined, and closed when the partition is left or stopped.
   *
   * <p>Only use this if the state represented by the metrics is independent of the transition/role
   * of the partition.
   */
  MeterRegistry getPartitionStartupMeterRegistry();

  /**
   * Returns a meter registry which wraps around the {@link #getPartitionStartupMeterRegistry()},
   * and is created/closed along with partition transitions.
   *
   * <p>This is very useful to add metrics which depend on objects/state recreated during a
   * transition, and is typically the registry you want to use.
   */
  MeterRegistry getPartitionTransitionMeterRegistry();

  void setPartitionTransitionMeterRegistry(MeterRegistry transitionMeterRegistry);
}
