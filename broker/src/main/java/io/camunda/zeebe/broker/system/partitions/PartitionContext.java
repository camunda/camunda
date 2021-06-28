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
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.io.IOException;
import java.util.List;

/**
 * Interface encapsulating all the information about a partition that are needed at runtime (i.e.
 * after the transition to the current role has completed)
 */
public interface PartitionContext {

  int getPartitionId();

  RaftPartition getRaftPartition();

  List<PartitionListener> getPartitionListeners();

  Role getCurrentRole();

  long getCurrentTerm();

  LogStream getLogStream();

  HealthMonitor getComponentHealthMonitor();

  StreamProcessor getStreamProcessor();

  AsyncSnapshotDirector getSnapshotDirector();

  ExporterDirector getExporterDirector();

  void setDiskSpaceAvailable(boolean b);

  boolean shouldProcess();

  void pauseProcessing() throws IOException;

  void resumeProcessing() throws IOException;

  boolean shouldExport();

  boolean pauseExporting() throws IOException;

  boolean resumeExporting() throws IOException;
}
