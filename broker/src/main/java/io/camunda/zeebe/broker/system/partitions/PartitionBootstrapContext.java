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

public interface PartitionBootstrapContext {

  BrokerCfg getBrokerCfg();

  PartitionMessagingService getMessagingService();

  int getPartitionId();

  int getNodeId();

  void setSnapshotReplication(SnapshotReplication replication);

  SnapshotReplication getSnapshotReplication();

  void setStateController(StateControllerImpl stateController);

  RaftPartition getRaftPartition();

  ConstructableSnapshotStore getConstructableSnapshotStore();

  ReceivableSnapshotStore getReceivableSnapshotStore();

  StateControllerImpl getStateController();

  void setLogDeletionService(LogDeletionService deletionService);

  ActorSchedulingService getActorSchedulingService();

  LogDeletionService getLogDeletionService();

  void setLogStream(LogStream logStream);

  LogStream getLogStream();

  void setZeebeDb(ZeebeDb db);

  StreamProcessor getStreamProcessor();

  void setStreamProcessor(StreamProcessor o);

  ZeebeDb getZeebeDb();

  CommandResponseWriter getCommandResponseWriter();

  Consumer<TypedRecord> getOnProcessedListener();

  TypedRecordProcessorsFactory getTypedRecordProcessorsFactory();

  void setSnapshotDirector(AsyncSnapshotDirector director);

  AsyncSnapshotDirector getSnapshotDirector();

  ActorControl getActorControl();

  void setActorControl(ActorControl actorControl);

  void setMetricsTimer(ScheduledTimer metricsTimer);

  ScheduledTimer getMetricsTimer();

  void setExporterDirector(ExporterDirector director);

  ExporterRepository getExporterRepository();

  ExporterDirector getExporterDirector();

  List<PartitionListener> getPartitionListeners();

  PartitionTransitionContext toTransitionContext();

  PartitionProcessingState getPartitionProcessingState();

  HealthMonitor getComponentHealthMonitor();
}
