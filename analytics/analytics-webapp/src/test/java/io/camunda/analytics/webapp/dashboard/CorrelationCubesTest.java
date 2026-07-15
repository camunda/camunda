/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import org.junit.jupiter.api.Test;

/**
 * The {@code corr-*} naming-convention classifier: dimension shape says which correlation-cube kind
 * a catalog dataset is, and a malformed shape (no {@code var.*} dimension, or more than one) is
 * rejected rather than guessed.
 */
final class CorrelationCubesTest {

  private final DatasetCompiler compiler =
      new DatasetCompiler(
          MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));

  @Test
  void shouldClassifyADurationCorrelationCube() {
    // given a corr-* cube with exactly one var.* dimension and a duration_p sketch meter
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-route", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", Transition.COMPLETED.name())
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .meter(Meter.of("duration_p", MeterCatalog.PERCENTILE, "durationMs"))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isEqualTo(CorrelationCubes.Kind.DURATION);
    assertThat(CorrelationCubes.variableDimension(dataset)).isEqualTo("var.route");
  }

  @Test
  void shouldClassifyAVariantCorrelationCube() {
    // given a corr-* cube with variantHash plus exactly one var.* dimension
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-variant-route", FactType.PROCESS_INSTANCE)
                .filterNotNull("variantHash")
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("variantHash", DimensionType.LONG)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isEqualTo(CorrelationCubes.Kind.VARIANT);
  }

  @Test
  void shouldClassifyABranchCorrelationCube() {
    // given a corr-* cube with elementId plus exactly one var.* dimension
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-branch-route", FactType.ELEMENT)
                .filterEquals("transition", Transition.COMPLETED.name())
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("elementId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isEqualTo(CorrelationCubes.Kind.BRANCH);
  }

  @Test
  void shouldRejectACubeWithNoVariableDimension() {
    // given a corr-* cube with no var.* dimension at all
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-orphan", FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
    assertThat(CorrelationCubes.variableDimension(dataset)).isNull();
  }

  @Test
  void shouldRejectACubeWithMoreThanOneVariableDimension() {
    // given a corr-* cube declaring two var.* dimensions — not a recognized shape
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-two-vars", FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .dimension("var.region", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
    assertThat(CorrelationCubes.variableDimension(dataset)).isNull();
  }

  @Test
  void shouldRejectADurationCubeMissingTheCountMeter() {
    // given a corr-* cube with the duration_p marker but no count meter — classified-but-malformed
    // would 400 the read forever once the planner rejects the undeclared 'count' meter
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-route", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", Transition.COMPLETED.name())
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("duration_p", MeterCatalog.PERCENTILE, "durationMs"))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectAVariantCubeMissingTheCountMeter() {
    // given a corr-* cube with variantHash + var.* but no count meter (a declaration needs at
    // least one meter, so an unrelated one stands in for "none of them is named count")
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-variant-route", FactType.PROCESS_INSTANCE)
                .filterNotNull("variantHash")
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("variantHash", DimensionType.LONG)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("other", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectABranchCubeMissingTheCountMeter() {
    // given a corr-* cube with elementId + var.* but no count meter (see the comment above)
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-branch-route", FactType.ELEMENT)
                .filterEquals("transition", Transition.COMPLETED.name())
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("elementId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("other", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectAStringTypedVariantHash() {
    // given a corr-* cube whose variantHash is typed STRING instead of LONG — the read path's
    // asLong would silently collapse every value to 0 rather than the real hash
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-variant-route", FactType.PROCESS_INSTANCE)
                .filterNotNull("variantHash")
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("variantHash", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectACubeWithBothVariantHashAndElementIdMarkers() {
    // given a corr-* cube declaring both markers — ambiguous, not one of the three shapes
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-both", FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("variantHash", DimensionType.LONG)
                .dimension("elementId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectACorrPrefixedCubeWithNoMarkerAndNoDurationMeter() {
    // given a corr-* cube with exactly one var.* dimension but neither a variantHash/elementId
    // marker nor a duration_p meter — not a recognized shape even though it has a count meter
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("corr-orphan-shape", FactType.PROCESS_INSTANCE)
                .filterNotNull("var.route")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("var.route", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  @Test
  void shouldRejectACubeWithoutTheCorrPrefix() {
    // given a var.*-grouped cube not named corr-* (e.g. the standard dispute-types cube)
    final CompiledDataset dataset =
        compile(
            DatasetDeclaration.builder("dispute-types", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", Transition.COMPLETED.name())
                .filterNotNull("var.customerId")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("var.type", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(60_000L)
                .build());

    // when / then — the naming convention gates classification, not just the dimension shape
    assertThat(CorrelationCubes.classify(dataset)).isNull();
  }

  private CompiledDataset compile(final DatasetDeclaration declaration) {
    return compiler.compile(1L, declaration);
  }
}
