/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import java.time.InstantSource;
import java.util.List;

/**
 * Wraps records for exporting. This class is designed for single-threaded use within the
 * ExporterDirector actor. It optimizes performance by lazily deserializing record values only
 * when at least one exporter needs to export them.
 *
 * <p>The typical usage pattern is:
 *
 * <ol>
 *   <li>Call {@link #wrap(LoggedEvent)} to read metadata and check if any exporter wants the
 *       record
 *   <li>Call {@link #export()} to lazily deserialize the value and export to all interested
 *       exporters
 * </ol>
 */
class RecordExporter {

  private final RecordValues recordValues = new RecordValues();
  private final RecordMetadata rawMetadata = new RecordMetadata();
  private final List<ExporterContainer> containers;
  private final TypedRecordImpl typedEvent;
  private final ExporterMetrics exporterMetrics;

  private boolean shouldExport;
  private int exporterIndex;
  private final InstantSource clock;
  // Holds the current event being processed. Set by wrap(), used by ensureValueRead().
  private LoggedEvent currentEvent;
  // Tracks whether the record value has been deserialized. Reset by wrap(), set by ensureValueRead().
  private boolean valueRead;

  RecordExporter(
      final ExporterMetrics exporterMetrics,
      final List<ExporterContainer> containers,
      final int partitionId,
      final InstantSource clock) {
    this.containers = containers;
    typedEvent = new TypedRecordImpl(partitionId);
    this.exporterMetrics = exporterMetrics;
    this.clock = clock;
  }

  void wrap(final LoggedEvent rawEvent) {
    rawEvent.readMetadata(rawMetadata);
    currentEvent = rawEvent;
    valueRead = false;

    // Check if any exporter wants to export this record based on metadata alone
    shouldExport = anyExporterAccepts(rawMetadata, rawEvent.getPosition());
    if (shouldExport) {
      exporterIndex = 0;
    }
  }

  private boolean anyExporterAccepts(final RecordMetadata metadata, final long position) {
    for (final ExporterContainer container : containers) {
      if (container.shouldExportRecord(metadata, position)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ensures the record value is deserialized. This is called lazily, only when we actually need
   * to export the record. Returns false if the value cannot be read (unsupported value type).
   *
   * <p>This method must only be called after wrap() has been invoked.
   */
  private boolean ensureValueRead() {
    if (!valueRead) {
      if (currentEvent == null) {
        throw new IllegalStateException("ensureValueRead() called before wrap()");
      }
      final UnifiedRecordValue recordValue =
          recordValues.readRecordValue(currentEvent, rawMetadata.getValueType());
      // If recordValue is null, it means the value type is not supported/known
      if (recordValue != null) {
        typedEvent.wrap(currentEvent, rawMetadata, recordValue);
        valueRead = true;
        return true;
      } else {
        // Value cannot be read for this record type
        return false;
      }
    }
    return true;
  }

  boolean export() {
    if (!shouldExport) {
      return true;
    }

    // Lazily deserialize the record value only when we actually need to export
    if (!ensureValueRead()) {
      // Value cannot be read (unsupported value type), skip this record
      return true;
    }

    final ValueType valueType = typedEvent.getValueType();
    // exporting latency tracks time
    // from record written to exporting of record started
    final long currentMillis = clock.millis();
    // we track this here already, even if it is not successful as otherwise
    // we might get no metric at all when exporting is not possible
    // this allows us to observe that exporting latency is increasing
    exporterMetrics.exportingLatency(valueType, typedEvent.getTimestamp(), currentMillis);

    final int exportersCount = containers.size();

    // current error handling strategy is simply to repeat forever until the record can be
    // successfully exported.
    while (exporterIndex < exportersCount) {
      final ExporterContainer container = containers.get(exporterIndex);

      try (final var timer =
          exporterMetrics.startExporterExportingTimer(valueType, container.getId())) {
        if (container.exportRecord(rawMetadata, typedEvent)) {
          exporterIndex++;
          exporterMetrics.setLastExportedPosition(container.getId(), typedEvent.getPosition());
        } else {
          return false;
        }
      }
    }

    return true;
  }

  TypedRecordImpl getTypedEvent() {
    return typedEvent;
  }

  public void resetExporterIndex() {
    exporterIndex = 0;
  }
}
