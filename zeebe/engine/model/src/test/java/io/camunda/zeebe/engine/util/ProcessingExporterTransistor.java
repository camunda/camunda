/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProcessingExporterTransistor implements StreamProcessorLifecycleAware {

  private final RecordValues recordValues = new RecordValues();
  private final RecordMetadata metadata = new RecordMetadata();

  private LogStreamReader logStreamReader;
  private TypedRecordImpl typedEvent;
  private final TestLogStream logStream;
  private final ExecutorService executorService;

  /** The recording exporter instance is needed in order to use the Exporter#export method. */
  private final RecordingExporter recordingExporter;

  public ProcessingExporterTransistor(final TestLogStream testLogStream) {
    logStream = testLogStream;
    executorService = Executors.newSingleThreadExecutor();
    recordingExporter = new RecordingExporter();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    executorService.submit(
        () -> {
          final int partitionId = context.getPartitionId();
          typedEvent = new TypedRecordImpl(partitionId);
          logStream.registerRecordAvailableListener(this::onNewEventCommitted);
          logStreamReader = logStream.newLogStreamReader();
          exportEvents();
        });
  }

  @Override
  public void onClose() {
    executorService.close();
  }

  private void onNewEventCommitted() {
    // this is called from outside (LogStream), so we need to enqueue the task
    if (executorService.isShutdown()) {
      return;
    }

    executorService.submit(this::exportEvents);
  }

  private void exportEvents() {
    // we need to skip until onRecovered happened
    if (logStreamReader == null) {
      return;
    }

    while (logStreamReader.hasNext()) {
      final LoggedEvent rawEvent = logStreamReader.next();
      metadata.reset();
      rawEvent.readMetadata(metadata);

      final UnifiedRecordValue recordValue =
          recordValues.readRecordValue(rawEvent, metadata.getValueType());
      typedEvent.wrap(rawEvent, metadata, recordValue);

      recordingExporter.export(typedEvent);
    }
  }
}
