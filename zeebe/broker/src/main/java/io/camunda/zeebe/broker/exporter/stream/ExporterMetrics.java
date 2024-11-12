/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.protocol.record.ValueType;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public final class ExporterMetrics {

  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_EXPORTER = "exporter";
  private static final String LABEL_NAME_ACTION = "action";
  private static final String LABEL_NAME_VALUE_TYPE = "valueType";
  private static final String NAMESPACE_ZEEBE = "zeebe";

  private static final Histogram EXPORTING_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporting_latency")
          .help("Time between a record is written until it is picked up for exporting (in seconds)")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_VALUE_TYPE)
          .register();

  private static final Histogram EXPORTER_EXPORTING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporter_exporting_duration")
          .help("The time an exporter needs to export certain record (duration in seconds)")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_EXPORTER, LABEL_NAME_VALUE_TYPE)
          .register();

  private static final Counter EXPORTER_EVENTS =
      Counter.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporter_events_total")
          .help("Number of events processed by exporter")
          .labelNames(LABEL_NAME_ACTION, LABEL_NAME_PARTITION, LABEL_NAME_VALUE_TYPE)
          .register();

  private static final Gauge LAST_EXPORTED_POSITION =
      Gauge.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporter_last_exported_position")
          .help("The last exported position by exporter and partition.")
          .labelNames(LABEL_NAME_EXPORTER, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge LAST_UPDATED_EXPORTED_POSITION =
      Gauge.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporter_last_updated_exported_position")
          .help("The last exported position which was also updated/committed by the exporter.")
          .labelNames(LABEL_NAME_EXPORTER, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge EXPORTER_PHASE =
      Gauge.build()
          .namespace(NAMESPACE_ZEEBE)
          .name("exporter_state")
          .help(
              "Describes the phase of the exporter, namely if it is exporting, paused or soft paused.")
          .labelNames(LABEL_NAME_PARTITION)
          .register();
  private final String partitionIdLabel;

  private final Gauge.Child exporterPhase;

  public ExporterMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
    exporterPhase = EXPORTER_PHASE.labels(partitionIdLabel);
  }

  private void event(final String action, final ValueType valueType) {
    EXPORTER_EVENTS.labels(action, partitionIdLabel, valueType.name()).inc();
  }

  public void setExporterActive() {
    exporterPhase.set(0);
  }

  public void setExporterPaused() {
    exporterPhase.set(1);
  }

  public void setExporterSoftPaused() {
    exporterPhase.set(2);
  }

  public void eventExported(final ValueType valueType) {
    event("exported", valueType);
  }

  public void eventSkipped(final ValueType valueType) {
    event("skipped", valueType);
  }

  public void setLastUpdatedExportedPosition(final String exporter, final long position) {
    LAST_UPDATED_EXPORTED_POSITION.labels(exporter, partitionIdLabel).set(position);
  }

  public void setLastExportedPosition(final String exporter, final long position) {
    LAST_EXPORTED_POSITION.labels(exporter, partitionIdLabel).set(position);
  }

  public void exportingLatency(
      final ValueType valueType, final long written, final long exporting) {
    EXPORTING_LATENCY
        .labels(partitionIdLabel, valueType.name())
        .observe((exporting - written) / 1000f);
  }

  public Histogram.Timer startExporterExportingTimer(
      final ValueType valueType, final String exporter) {
    return EXPORTER_EXPORTING_DURATION
        .labels(partitionIdLabel, exporter, valueType.name())
        .startTimer();
  }

  public void initializeExporterState(final ExporterPhase state) {
    switch (state) {
      case PAUSED:
        setExporterPaused();
        break;
      case SOFT_PAUSED:
        setExporterSoftPaused();
        break;
      default:
        setExporterActive();
        break;
    }
  }
}
