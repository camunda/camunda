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
import java.util.List;
import org.junit.jupiter.api.Test;

final class DatasetDeclarationTest {

  @Test
  void shouldRejectSnapshotsNotAlignedWithTheFinestWindow() {
    // given / when / then: a snapshot grid that is not a multiple of the finest window
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("bad-snapshots", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(60_000L)
                    .snapshots(90_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a multiple of its finest window");
  }

  @Test
  void shouldAcceptSnapshotsOnTheFinestWindowGrid() {
    // given / when
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("good-snapshots", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .snapshots(300_000L)
            .build();

    // then
    assertThat(declaration.snapshotEveryMs()).isEqualTo(300_000L);
  }

  @Test
  void shouldRejectEvictionPredicatesOnAnAggregatedDataset() {
    // given / when / then: eviction deletes keyed rows, which only TABLE datasets have
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("bad-evict", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(60_000L)
                    .evictWhen(FilterPredicate.notEquals("transition", "ACTIVATED"))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only TABLE datasets evict rows");
  }

  @Test
  void shouldAcceptEvictionPredicatesOnATable() {
    // given / when
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("open-rows", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", "ACTIVATED")
            .asTable("processInstanceKey")
            .evictWhen(FilterPredicate.notEquals("transition", "ACTIVATED"))
            .dimension("bpmnProcessId", DimensionType.STRING)
            .build();

    // then
    assertThat(declaration.evictionFilters())
        .singleElement()
        .satisfies(f -> assertThat(f.field()).isEqualTo("transition"));
  }

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
  void shouldRejectATextDimensionOnAnAggregatedDataset() {
    // given / when / then: TEXT is a large payload column type (e.g. BPMN XML), never a grouping
    // key — as a grain column it cannot be indexed and its driver values break key equality
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("text-grain", FactType.PROCESS_INSTANCE)
                    .dimension("payload", DimensionType.TEXT)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be a grouping key");
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
  void shouldAcceptMinuteHourDayTiers() {
    // given the canonical 1m/1h/1d tiering
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("tiered", FactType.PROCESS_INSTANCE)
            .meter(Meter.of("n", MeterCatalog.COUNT))
            .window(60_000L)
            .window(3_600_000L)
            .window(86_400_000L)
            .build();

    // then it is accepted as declared
    assertThat(declaration.windowSizesMs()).containsExactly(60_000L, 3_600_000L, 86_400_000L);
  }

  @Test
  void shouldRejectDuplicateWindowTiers() {
    // when a tier is declared twice, then the declaration is rejected naming dataset and size
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("dup", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(60_000L)
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataset 'dup'")
        .hasMessageContaining("strictly ascending")
        .hasMessageContaining("60000");
  }

  @Test
  void shouldRejectUnorderedWindowTiers() {
    // when tiers are declared coarse-to-fine, then the declaration is rejected naming both sizes
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("unordered", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(3_600_000L)
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataset 'unordered'")
        .hasMessageContaining("60000")
        .hasMessageContaining("3600000");
  }

  @Test
  void shouldRejectTierThatIsNotAMultipleOfTheFinestTier() {
    // when a 90s/120s pair is declared (120s is not a multiple of 90s), then it is rejected —
    // Stage 2 rolls coarse cells up from finest-tier cells, so a non-multiple tier is silently
    // wrong
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("skewed", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(90_000L)
                    .window(120_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataset 'skewed'")
        .hasMessageContaining("120000")
        .hasMessageContaining("90000")
        .hasMessageContaining("multiple");
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

  @Test
  void shouldRejectMeterNameThatCollidesWithTheAggIdKeyEncoding() {
    // given a meter literally named like the registry key of meter 'foo' at the 60s tier
    // when declared, then it is rejected — the aggId key is <name>@<windowMs>, so '@' is forbidden
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .meter(Meter.of("foo@60000", MeterCatalog.COUNT))
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataset 'd'")
        .hasMessageContaining("foo@60000");
  }

  @Test
  void shouldRejectDatasetNameWithWhitespaceQuoteOrAt() {
    for (final String bad : List.of("my dataset", "name'quote", "name\"quote", "name@tier")) {
      assertThatThrownBy(
              () ->
                  DatasetDeclaration.builder(bad, FactType.PROCESS_INSTANCE)
                      .meter(Meter.of("n", MeterCatalog.COUNT))
                      .window(60_000L)
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(bad);
    }
  }

  @Test
  void shouldAcceptQuestionStyleDatasetNames() {
    // given the report builder's derived name shape (carries ':', '=', ',', '.', '-')
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(
                "q:process-instances:duration-percentile:percentile=95.0:by:bpmnProcessId,:g:60000",
                FactType.PROCESS_INSTANCE)
            .meter(Meter.of("percentile", MeterCatalog.PERCENTILE, "durationMs"))
            .window(60_000L)
            .build();

    // then it is accepted
    assertThat(declaration.name()).startsWith("q:process-instances");
  }

  @Test
  void shouldRejectUnsafeStructuralDimensionName() {
    // when a structural dimension is not a plain identifier (it becomes a physical column)
    assertThatThrownBy(
            () ->
                DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                    .dimension("bad column", DimensionType.STRING)
                    .meter(Meter.of("n", MeterCatalog.COUNT))
                    .window(60_000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataset 'd'")
        .hasMessageContaining("bad column");
  }

  @Test
  void shouldAcceptVariableDimensionWithNamespacedDots() {
    // given a variable name that itself contains dots (folded to '_' by the serving layer)
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
            .dimension("var.customer.region", DimensionType.STRING)
            .meter(Meter.of("n", MeterCatalog.COUNT))
            .window(60_000L)
            .build();

    // then it is accepted (variable names are user process data; dots are legal)
    assertThat(declaration.dimensions())
        .extracting(DimensionSpec::name)
        .containsExactly("var.customer.region");
  }

  @Test
  void shouldRejectVariableDimensionThatBreaksTheColumnDerivation() {
    // when the variable name carries what the physical identifier allowlist forbids
    for (final String bad : List.of("var.", "var.a'b", "var.a b", "var.a-b", "var.a@b")) {
      assertThatThrownBy(
              () ->
                  DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                      .dimension(bad, DimensionType.STRING)
                      .meter(Meter.of("n", MeterCatalog.COUNT))
                      .window(60_000L)
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("dataset 'd'")
          .hasMessageContaining(bad);
    }
  }
}
