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
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.util.List;

/**
 * Interface encapsulating all the information about a partition that are needed at runtime (i.e.
 * after the transition to the current role has completed)
 */
public interface PartitionContext {

  int getPartitionId();

  RaftPartition getRaftPartition();

  @Deprecated // will be moved to transition logic and happen automatically
  List<ActorFuture<Void>> notifyListenersOfBecomingLeader(final long newTerm);

  @Deprecated // will be moved to transition logic and happen automatically
  List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm);

  @Deprecated // will be moved to transition logic and happen automatically
  void notifyListenersOfBecomingInactive();

  Role getCurrentRole();

  long getCurrentTerm();

  HealthMonitor getComponentHealthMonitor();

  StreamProcessor getStreamProcessor();

  @Deprecated // will be moved into some kind of controller class
  void triggerSnapshot();

  ExporterDirector getExporterDirector();

  @Deprecated // currently the implementation forwards this to other components inside the
  // partition; these components will be directly registered as listeners in the future
  void setDiskSpaceAvailable(boolean b);

  @Deprecated // will be moved into some kind of controller class
  boolean shouldProcess();

  @Deprecated // will be moved into some kind of controller class
  void pauseProcessing() throws IOException;

  @Deprecated // will be moved into some kind of controller class
  void resumeProcessing() throws IOException;

  @Deprecated // will be moved into some kind of controller class
  boolean shouldExport();

  @Deprecated // will be moved into some kind of controller class
  boolean pauseExporting() throws IOException;

  @Deprecated // will be moved into some kind of controller class
  boolean resumeExporting() throws IOException;
}
