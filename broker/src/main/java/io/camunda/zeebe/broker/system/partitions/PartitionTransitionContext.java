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
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
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
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

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

  CommandResponseWriter getCommandResponseWriter();

  Consumer<TypedRecord<?>> getOnProcessedListener();

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

  boolean shouldExport();

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

  GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer();
}
