/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.io.IOException;
import java.util.function.Supplier;

public class PartitionAdminControlImpl implements PartitionAdminControl {

  private final Supplier<StreamProcessor> streamProcessorSupplier;
  private final Supplier<AsyncSnapshotDirector> snapshotDirectorSupplier;
  private final Supplier<PartitionProcessingState> partitionProcessingStateSupplier;
  private final Supplier<ZeebeDb> zeebeDbSupplier;
  private final Supplier<LogStream> logStreamSupplier;
  private final Supplier<Boolean> migrationSnapshotTakenSupplier;

  public PartitionAdminControlImpl(
      final Supplier<StreamProcessor> streamProcessorSupplier,
      final Supplier<AsyncSnapshotDirector> snapshotDirectorSupplier,
      final Supplier<PartitionProcessingState> partitionProcessingStateSupplier,
      final Supplier<ZeebeDb> zeebeDbSupplier,
      final Supplier<LogStream> logStreamSupplier,
      final Supplier<Boolean> migrationSnapshotTakenSupplier) {
    this.streamProcessorSupplier = streamProcessorSupplier;
    this.snapshotDirectorSupplier = snapshotDirectorSupplier;
    this.partitionProcessingStateSupplier = partitionProcessingStateSupplier;
    this.zeebeDbSupplier = zeebeDbSupplier;
    this.logStreamSupplier = logStreamSupplier;
    this.migrationSnapshotTakenSupplier = migrationSnapshotTakenSupplier;
  }

  @Override
  public StreamProcessor getStreamProcessor() {
    return streamProcessorSupplier.get();
  }

  @Override
  public ZeebeDb getZeebeDb() {
    return zeebeDbSupplier.get();
  }

  @Override
  public LogStream getLogStream() {
    return logStreamSupplier.get();
  }

  @Override
  public void triggerSnapshot() {
    snapshotDirectorSupplier.get().forceSnapshot();
  }

  @Override
  public boolean shouldProcess() {
    return partitionProcessingStateSupplier.get().shouldProcess();
  }

  @Override
  public void pauseProcessing() throws IOException {
    partitionProcessingStateSupplier.get().pauseProcessing();
  }

  @Override
  public void resumeProcessing() throws IOException {
    partitionProcessingStateSupplier.get().resumeProcessing();
  }

  @Override
  public boolean isMigrationSnapshotTaken() {
    return migrationSnapshotTakenSupplier.get();
  }
}
