/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.io.IOException;

public interface PartitionAdminControl {
  StreamProcessor getStreamProcessor();

  ZeebeDb getZeebeDb();

  LogStream getLogStream();

  void triggerSnapshot();

  boolean shouldProcess();

  void pauseProcessing() throws IOException;

  void resumeProcessing() throws IOException;

  /**
   * {@code true} once a snapshot capturing this replica's migrated state has been taken since it
   * last ran its migrations (see {@code MigrationSnapshotDirector}). Never resets to {@code false}
   * once set.
   */
  boolean isMigrationSnapshotTaken();
}
