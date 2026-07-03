/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import org.junit.jupiter.api.Test;

final class DatasetDeclarationTest {

  @Test
  void shouldBuildADeclaration() {
    // given: "duration by definition and region (from the completion snapshot), per minute & hour"
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("pi-duration", FactType.PROCESS_INSTANCE)
            .filterEquals("bpmnProcessId", "invoice")
            .dimension("processDefinitionKey", DimensionType.LONG)
            .dimension("var.region", DimensionType.STRING, EnrichmentTiming.PI_COMPLETE)
            .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs"))
            .window(60_000L)
            .window(3_600_000L)
            .lateness(5_000L)
            .build();

    // then
    assertThat(declaration.sourceFact()).isEqualTo(FactType.PROCESS_INSTANCE);
    assertThat(declaration.dimensions())
        .extracting(DimensionSpec::name)
        .containsExactly("processDefinitionKey", "var.region");
    assertThat(declaration.dimensions().get(1).enrichment())
        .isEqualTo(EnrichmentTiming.PI_COMPLETE);
    assertThat(declaration.filters())
        .singleElement()
        .satisfies(f -> assertThat(f.field()).isEqualTo("bpmnProcessId"));
    assertThat(declaration.meters()).extracting(Meter::name).containsExactly("duration");
    assertThat(declaration.windowSizesMs()).containsExactly(60_000L, 3_600_000L);
  }

  @Test
  void shouldRejectDuplicateDimensionNames() {
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .dimension("region", DimensionType.STRING)
                    .dimension("region", DimensionType.STRING)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dimension names must be unique");
  }

  @Test
  void shouldRejectDuplicateMeterNames() {
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("m", MeterCatalog.COUNT))
                    .meter(Meter.of("m", MeterCatalog.SUM, "x"))
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("meter names must be unique");
  }

  @Test
  void shouldRejectNoMetersOrNoWindows() {
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE).window(60_000L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no meters");
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no window tiers");
  }

  @Test
  void shouldRejectNonPositiveWindow() {
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(0L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-positive window");
  }
}
