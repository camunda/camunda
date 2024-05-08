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
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import java.util.List;

class RecordExporter {

  private final RecordValues recordValues = new RecordValues();
  private final RecordMetadata rawMetadata = new RecordMetadata();
  private final List<ExporterContainer> containers;
  private final TypedRecordImpl typedEvent;
  private final ExporterMetrics exporterMetrics;

  private boolean shouldExport;
  private int exporterIndex;

  RecordExporter(
      final ExporterMetrics exporterMetrics,
      final List<ExporterContainer> containers,
      final int partitionId) {
    this.containers = containers;
    typedEvent = new TypedRecordImpl(partitionId);
    this.exporterMetrics = exporterMetrics;
  }

  void wrap(final LoggedEvent rawEvent) {
    rawEvent.readMetadata(rawMetadata);

    final UnifiedRecordValue recordValue =
        recordValues.readRecordValue(rawEvent, rawMetadata.getValueType());

    shouldExport = recordValue != null;
    if (shouldExport) {
      typedEvent.wrap(rawEvent, rawMetadata, recordValue);
      exporterIndex = 0;
    }
  }

  boolean export() {
    if (!shouldExport) {
      return true;
    }

    final int exportersCount = containers.size();

    // current error handling strategy is simply to repeat forever until the record can be
    // successfully exported.
    while (exporterIndex < exportersCount) {
      final ExporterContainer container = containers.get(exporterIndex);

      if (container.exportRecord(rawMetadata, typedEvent)) {
        exporterIndex++;
        exporterMetrics.setLastExportedPosition(container.getId(), typedEvent.getPosition());
      } else {
        return false;
      }
    }

    return true;
  }

  TypedRecordImpl getTypedEvent() {
    return typedEvent;
  }
}
