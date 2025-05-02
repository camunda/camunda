/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc.ExporterActionKeyNames;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ExporterMetrics {
  private static final String LABEL_NAME_EXPORTER = "exporter";
  private static final String LABEL_NAME_ACTION = "action";
  private static final String LABEL_NAME_VALUE_TYPE = "valueType";

  private final Map<String, AtomicLong> lastExportedPositions = new HashMap<>();
  private final Map<String, AtomicLong> lastUpdatedExportedPositions = new HashMap<>();
  private final AtomicInteger exporterState = new AtomicInteger();
  private final Map<ValueType, Timer> exportingLatency = new HashMap<>();
  private final Table<String, ValueType, Timer> exporterExportingDuration = Table.simple();
  private final Table<ExporterActionKeyNames, ValueType, Counter> exporterEvents =
      Table.ofEnum(ExporterActionKeyNames.class, ValueType.class, Counter[]::new);

  private final MeterRegistry meterRegistry;

  public ExporterMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
  }

  public void setExporterActive() {
    exporterState.set(0);
  }

  public void setExporterPaused() {
    exporterState.set(1);
  }

  public void setExporterSoftPaused() {
    exporterState.set(2);
  }

  public void eventExported(final ValueType valueType) {
    event(ExporterActionKeyNames.EXPORTED, valueType);
  }

  public void eventSkipped(final ValueType valueType) {
    event(ExporterActionKeyNames.SKIPPED, valueType);
  }

  public void setLastUpdatedExportedPosition(final String exporter, final long position) {
    lastUpdatedExportedPositions
        .computeIfAbsent(
            exporter,
            id ->
                registerPerExporterGauge(
                    ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION, id, position))
        .set(position);
  }

  public void setLastExportedPosition(final String exporter, final long position) {
    lastExportedPositions
        .computeIfAbsent(
            exporter,
            id -> registerPerExporterGauge(ExporterMetricsDoc.LAST_EXPORTED_POSITION, id, position))
        .set(position);
  }

  public void exportingLatency(
      final ValueType valueType, final long written, final long exporting) {
    exportingLatency
        .computeIfAbsent(valueType, this::registerExportingLatency)
        .record(exporting - written, TimeUnit.MILLISECONDS);
  }

  public CloseableSilently startExporterExportingTimer(
      final ValueType valueType, final String exporter) {
    final var timer =
        exporterExportingDuration.computeIfAbsent(
            exporter, valueType, this::registerExportingDuration);
    return MicrometerUtil.timer(timer, Timer.start(meterRegistry));
  }

  private void event(final ExporterActionKeyNames action, final ValueType valueType) {
    exporterEvents
        .computeIfAbsent(action, valueType, this::registerExporterEventCounter)
        .increment();
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

    final ExtendedMeterDocumentation meterDoc = ExporterMetricsDoc.EXPORTER_STATE;
    Gauge.builder(meterDoc.getName(), exporterState, Number::intValue)
        .description(meterDoc.getDescription())
        .register(meterRegistry);
  }

  private Counter registerExporterEventCounter(
      final ExporterActionKeyNames action, final ValueType valueType) {
    final var meterDoc = ExporterMetricsDoc.EXPORTER_EVENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(LABEL_NAME_ACTION, action.asString())
        .tag(LABEL_NAME_VALUE_TYPE, valueType.name())
        .register(meterRegistry);
  }

  private AtomicLong registerPerExporterGauge(
      final ExtendedMeterDocumentation meterDoc,
      final String exporterId,
      final long initialPosition) {
    final var position = new AtomicLong(initialPosition);
    Gauge.builder(meterDoc.getName(), position, Number::longValue)
        .tag(LABEL_NAME_EXPORTER, exporterId)
        .description(meterDoc.getDescription())
        .register(meterRegistry);
    return position;
  }

  private Timer registerExportingDuration(final String exporterId, final ValueType valueType) {
    final var meterDoc = ExporterMetricsDoc.EXPORTING_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(LABEL_NAME_VALUE_TYPE, valueType.name())
        .tag(LABEL_NAME_EXPORTER, exporterId)
        .register(meterRegistry);
  }

  private Timer registerExportingLatency(final ValueType valueType) {
    final var meterDoc = ExporterMetricsDoc.EXPORTING_LATENCY;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(LABEL_NAME_VALUE_TYPE, valueType.name())
        .register(meterRegistry);
  }
}
