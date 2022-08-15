/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public final class StreamProcessorMetrics {

  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_RECORD_TYPE = "recordType";
  private static final String LABEL_NAME_ACTION = "action";
  private static final String LABEL_NAME_VALUE_TYPE = "valueType";
  private static final String LABEL_NAME_INTENT = "intent";

  private static final String LABEL_WRITTEN = "written";
  private static final String LABEL_SKIPPED = "skipped";
  private static final String LABEL_PROCESSED = "processed";
  private static final String NAMESPACE = "zeebe";

  private static final Counter STREAM_PROCESSOR_EVENTS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("stream_processor_records_total")
          .help("Number of records processed by stream processor")
          .labelNames(LABEL_NAME_ACTION, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge LAST_PROCESSED_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_last_processed_position")
          .help("The last position the stream processor has processed.")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Histogram PROCESSING_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_latency")
          .help(
              "Time between a command is written until it is picked up for processing (in seconds)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();
  private static final Histogram PROCESSING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_processing_duration")
          .help("Time for processing a record (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram PROCESS_RECORD_IN_PROCESSOR_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_process_record_in_processor_duration")
          .help("Time for processing a record (in seconds) by the processor")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram WRITE_RECORDS_TO_LOG_STREAM =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_write_records_to_log_stream")
          .help("Time for writing records to the log stream (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram UPDATE_STATE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_update_state")
          .help("Time for updating the state in RocksDB (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram EXECUTE_SIDE_EFFECTS =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_execute_side_effects")
          .help("Time for executing side effects (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram ON_ERROR =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_on_error")
          .help("Time for rolling back changes on error (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Histogram ERROR_HANDLING_IN_TRANSACTION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_error_handling_in_transaction")
          .help("Time for handling error in transaction (in seconds)")
          .labelNames(
              LABEL_NAME_RECORD_TYPE,
              LABEL_NAME_PARTITION,
              LABEL_NAME_VALUE_TYPE,
              LABEL_NAME_INTENT)
          .register();

  private static final Gauge STARTUP_RECOVERY_TIME =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_startup_recovery_time")
          .help("Time taken for startup and recovery of stream processor (in ms)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();
  private final String partitionIdLabel;

  public StreamProcessorMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(final String action) {
    STREAM_PROCESSOR_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void processingLatency(final long written, final long processed) {
    PROCESSING_LATENCY.labels(partitionIdLabel).observe((processed - written) / 1000f);
  }

  public Histogram.Timer startProcessingDurationTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return PROCESSING_DURATION
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startProcessRecordInProcessorTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return PROCESS_RECORD_IN_PROCESSOR_DURATION
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startWriteRecordsToLogStreamTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return WRITE_RECORDS_TO_LOG_STREAM
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startUpdateStateTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return UPDATE_STATE
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startExecuteSideEffectsTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return EXECUTE_SIDE_EFFECTS
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startOnErrorTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return ON_ERROR
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  public Histogram.Timer startErrorHandlingInTransactionTimer(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return ERROR_HANDLING_IN_TRANSACTION
        .labels(recordType.name(), partitionIdLabel, valueType.name(), intent.name())
        .startTimer();
  }

  /** We only process commands. */
  public void commandsProcessed() {
    event(LABEL_PROCESSED);
  }

  /**
   * We write various type of records. The positions are always increasing and incremented by 1 for
   * one record.
   */
  public void recordsWritten(final long amount) {
    if (amount < 1) {
      return;
    }

    STREAM_PROCESSOR_EVENTS.labels(LABEL_WRITTEN, partitionIdLabel).inc(amount);
  }

  /** We skip events on processing. */
  public void eventSkipped() {
    event(LABEL_SKIPPED);
  }

  public Gauge.Timer startRecoveryTimer() {
    return STARTUP_RECOVERY_TIME.labels(partitionIdLabel).startTimer();
  }

  public void setLastProcessedPosition(final long position) {
    LAST_PROCESSED_POSITION.labels(partitionIdLabel).set(position);
  }
}
