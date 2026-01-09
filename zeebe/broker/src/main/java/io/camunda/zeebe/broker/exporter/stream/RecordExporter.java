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

class RecordExporter {

  private final RecordValues recordValues = new RecordValues();
  private final RecordMetadata rawMetadata = new RecordMetadata();
  private final List<ExporterContainer> containers;
  private final TypedRecordImpl typedEvent;
  private final ExporterMetrics exporterMetrics;

  private boolean shouldExport;
  private int exporterIndex;
  private final InstantSource clock;
  private LoggedEvent currentEvent;
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

  private void ensureValueRead() {
    if (!valueRead) {
      final UnifiedRecordValue recordValue =
          recordValues.readRecordValue(currentEvent, rawMetadata.getValueType());
      // If recordValue is null, it means the value type is not supported/known
      // In this case, we cannot export the record
      if (recordValue != null) {
        typedEvent.wrap(currentEvent, rawMetadata, recordValue);
        valueRead = true;
      } else {
        // Mark as should not export if we can't read the value
        shouldExport = false;
      }
    }
  }

  boolean export() {
    if (!shouldExport) {
      return true;
    }

    // Ensure the record value is read before starting to export
    ensureValueRead();

    // After ensuring the value is read, check again if we should export
    // (ensureValueRead might set shouldExport to false if the value cannot be read)
    if (!shouldExport) {
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
