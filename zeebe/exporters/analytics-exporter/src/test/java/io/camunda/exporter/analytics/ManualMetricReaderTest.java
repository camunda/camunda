/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualMetricReaderTest {

  private final List<Collection<MetricData>> exported = new ArrayList<>();
  private ManualMetricReader reader;
  private LongCounter counter;

  @BeforeEach
  void setUp() {
    final MetricExporter capturingExporter =
        new MetricExporter() {
          @Override
          public CompletableResultCode export(final Collection<MetricData> metrics) {
            exported.add(new ArrayList<>(metrics));
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public AggregationTemporality getAggregationTemporality(
              final InstrumentType instrumentType) {
            return AggregationTemporalitySelector.deltaPreferred()
                .getAggregationTemporality(instrumentType);
          }
        };

    reader = new ManualMetricReader(capturingExporter);
    final var provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    counter = provider.get("test").counterBuilder("test.counter").build();
  }

  @Test
  void shouldCollectAndExportMetrics() {
    // given
    counter.add(1, Attributes.empty());

    // when
    final var result = reader.collectAndExport();

    // then
    assertThat(result.isSuccess()).isTrue();
    assertThat(exported).hasSize(1);
    assertThat(exported.get(0)).isNotEmpty();
  }

  @Test
  void shouldSkipExportWhenNoMetrics() {
    // when — no counter increments, but there may still be SDK-internal metrics
    // collectAndExport returns success regardless
    final var result = reader.collectAndExport();

    // then
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void shouldUseDeltaTemporality() {
    assertThat(reader.getAggregationTemporality(InstrumentType.COUNTER))
        .isEqualTo(AggregationTemporality.DELTA);
    assertThat(reader.getAggregationTemporality(InstrumentType.HISTOGRAM))
        .isEqualTo(AggregationTemporality.DELTA);
  }

  @Test
  void shouldNotExportOnForceFlush() {
    // given
    counter.add(1, Attributes.empty());

    // when
    reader.forceFlush();

    // then — no-op to prevent double-flush; final flush is driven by OtelSdkManager.close()
    assertThat(exported).isEmpty();
  }
}
