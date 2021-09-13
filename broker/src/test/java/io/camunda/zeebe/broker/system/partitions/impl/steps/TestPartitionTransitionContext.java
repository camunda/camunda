/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.PartitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.function.Consumer;

public class TestPartitionTransitionContext implements PartitionTransitionContext {

  private RaftPartition raftPartition;
  private Role currentRole;
  private long currentTerm;
  private HealthMonitor healthMonitor;
  private TypedRecordProcessorFactory streamProcessorFactory;
  private ExporterDirector exporterDirector;
  private LogStream logStream;
  private StreamProcessor streamProcessor;
  private ActorSchedulingService actorSchedulingService;
  private ZeebeDb zeebeDB;
  private TestConcurrencyControl concurrencyControl;

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
    return null;
  }

  @Override
  public List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm) {
    return null;
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
    return false;
  }

  @Override
  public void setDiskSpaceAvailable(final boolean b) {}

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
  public CommandResponseWriter getCommandResponseWriter() {
    return null;
  }

  @Override
  public Consumer<TypedRecord<?>> getOnProcessedListener() {
    return null;
  }

  @Override
  public TypedRecordProcessorFactory getStreamProcessorFactory() {
    return streamProcessorFactory;
  }

  public void setStreamProcessorFactory(final TypedRecordProcessorFactory streamProcessorFactory) {
    this.streamProcessorFactory = streamProcessorFactory;
  }

  @Override
  public void setZeebeDb(final ZeebeDb zeebeDb) {
    zeebeDB = zeebeDb;
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
  public AsyncSnapshotDirector getSnapshotDirector() {
    return null;
  }

  @Override
  public StateControllerImpl getStateController() {
    return null;
  }

  @Override
  public ConstructableSnapshotStore getConstructableSnapshotStore() {
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

  public void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }
}
