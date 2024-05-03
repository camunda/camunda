/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.io.IOException;
import java.util.function.Supplier;

public class PartitionAdminControlImpl implements PartitionAdminControl {

  private final Supplier<StreamProcessor> streamProcessorSupplier;
  private final Supplier<ExporterDirector> exporterDirectorSupplier;
  private final Supplier<AsyncSnapshotDirector> snapshotDirectorSupplier;
  private final Supplier<PartitionProcessingState> partitionProcessingStateSupplier;
  private final Supplier<ZeebeDb> zeebeDbSupplier;
  private final Supplier<LogStream> logStreamSupplier;

  public PartitionAdminControlImpl(
      final Supplier<StreamProcessor> streamProcessorSupplier,
      final Supplier<ExporterDirector> exporterDirectorSupplier,
      final Supplier<AsyncSnapshotDirector> snapshotDirectorSupplier,
      final Supplier<PartitionProcessingState> partitionProcessingStateSupplier,
      final Supplier<ZeebeDb> zeebeDbSupplier,
      final Supplier<LogStream> logStreamSupplier) {
    this.streamProcessorSupplier = streamProcessorSupplier;
    this.exporterDirectorSupplier = exporterDirectorSupplier;
    this.snapshotDirectorSupplier = snapshotDirectorSupplier;
    this.partitionProcessingStateSupplier = partitionProcessingStateSupplier;
    this.zeebeDbSupplier = zeebeDbSupplier;
    this.logStreamSupplier = logStreamSupplier;
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
  public ExporterDirector getExporterDirector() {
    return exporterDirectorSupplier.get();
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
  public boolean shouldExport() {
    return !partitionProcessingStateSupplier.get().isExportingPaused();
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
  public boolean pauseExporting() throws IOException {
    return partitionProcessingStateSupplier.get().pauseExporting();
  }

  @Override
  public boolean softPauseExporting() {
    return partitionProcessingStateSupplier.get().softPauseExporting();
  }

  @Override
  public boolean resumeExporting() throws IOException {
    return partitionProcessingStateSupplier.get().resumeExporting();
  }
}
