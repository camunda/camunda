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

public interface PartitionStartupContext {

  // provided by application-wide dependencies
  BrokerCfg getBrokerCfg();

  int getNodeId();

  RaftPartition getRaftPartition();

  int getPartitionId();

  ActorSchedulingService getActorSchedulingService();

  PartitionMessagingService getMessagingService();

  ConstructableSnapshotStore getConstructableSnapshotStore();

  ReceivableSnapshotStore getReceivableSnapshotStore();

  CommandResponseWriter getCommandResponseWriter();

  Consumer<TypedRecord<?>> getOnProcessedListener();

  ExporterRepository getExporterRepository();

  List<PartitionListener> getPartitionListeners();

  // injected before bootstrap
  /**
   * Returns the {@link ActorControl} of {@link ZeebePartition}
   *
   * @return {@link ActorControl} of {@link ZeebePartition}
   */
  ActorControl getActorControl();

  void setActorControl(ActorControl actorControl);

  HealthMonitor getComponentHealthMonitor();

  void setComponentHealthMonitor(final HealthMonitor healthMonitor);

  StateController getStateController();

  LogDeletionService getLogDeletionService();

  void setLogDeletionService(final LogDeletionService deletionService);

  LogStream getLogStream();

  void setLogStream(final LogStream logStream);

  ZeebeDb getZeebeDb();

  void setZeebeDb(final ZeebeDb<?> db);

  StreamProcessor getStreamProcessor();

  void setStreamProcessor(final StreamProcessor o);

  AsyncSnapshotDirector getSnapshotDirector();

  void setSnapshotDirector(final AsyncSnapshotDirector director);

  ScheduledTimer getMetricsTimer();

  void setMetricsTimer(final ScheduledTimer metricsTimer);

  ExporterDirector getExporterDirector();

  void setExporterDirector(ExporterDirector director);

  // can be called any time after bootstrap has completed
  PartitionStartupAndTransitionContextImpl createTransitionContext();
}
