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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DatasetCompilerTest {

  private final DatasetCompiler compiler =
      new DatasetCompiler(
          MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));

  private static DatasetDeclaration declaration() {
    return DatasetDeclaration.builder("pi-duration", FactType.PROCESS_INSTANCE)
        .filterEquals("bpmnProcessId", "invoice")
        .dimension("processDefinitionKey", DimensionType.LONG)
        .dimension("var.region", DimensionType.STRING, EnrichmentTiming.PI_COMPLETE)
        .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs"))
        .meter(Meter.of("count", MeterCatalog.COUNT))
        .window(60_000L)
        .window(3_600_000L)
        .lateness(5_000L)
        .build();
  }

  @Test
  void shouldDeriveGrainKeySelectorAndSchema() {
    // when
    final CompiledDataset compiled = compiler.compile(1L, declaration());

    // then the grain is the declared dimensions in order
    assertThat(compiled.grain().columns())
        .containsExactly(
            new DimensionColumn("processDefinitionKey", DimensionType.LONG),
            new DimensionColumn("var.region", DimensionType.STRING));
    // the key selector reads those dimensions off a fact
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("processDefinitionKey", 100L)
            .field("var.region", "EU")
            .build();
    assertThat(new DimensionKeySelector(compiled.grain()).getKey(fact))
        .isEqualTo(DimensionKey.of(compiled.grain(), 100L, "EU"));
    // the serving schema has one column per meter
    assertThat(compiled.schema().meterNames()).containsExactly("duration", "count");
  }

  @Test
  void shouldBindFiltersAndVariableEnrichment() {
    // when
    final CompiledDataset compiled = compiler.compile(1L, declaration());

    // then the fact binding carries the source, filters, and per-variable enrichment timing
    assertThat(compiled.factBinding().factType()).isEqualTo(FactType.PROCESS_INSTANCE);
    assertThat(compiled.factBinding().filters())
        .singleElement()
        .satisfies(f -> assertThat(f.value()).isEqualTo("invoice"));
    assertThat(compiled.factBinding().variableEnrichment())
        .containsExactly(entry("var.region", EnrichmentTiming.PI_COMPLETE));
  }

  @Test
  void shouldCompileOneMeterSlotPerDeclaredMeterAndOneTierPerWindow() {
    // when
    final CompiledDataset compiled = compiler.compile(1L, declaration());

    // then the meters are the composite's slots (declaration order) and the tiers are the cube's
    // windows, finest first, each with a distinct durable cell group; the shuffle stream is one
    // per cube (ADR 0009)
    assertThat(compiled.meters())
        .extracting(CompiledMeter::meterName)
        .containsExactly("duration", "count");
    assertThat(compiled.tiers())
        .extracting(CompiledTier::windowMs)
        .containsExactly(60_000L, 3_600_000L);
    assertThat(compiled.tiers()).extracting(CompiledTier::cellGroup).doesNotHaveDuplicates();
    assertThat(compiled.tiers())
        .extracting(CompiledTier::cellGroup)
        .doesNotContain(compiled.streamId());
    assertThat(compiled.finestTier().windowMs()).isEqualTo(60_000L);
  }

  private static DatasetDeclaration withMeter(final Meter meter) {
    return DatasetDeclaration.builder("param-check", FactType.PROCESS_INSTANCE)
        .dimension("bpmnProcessId", DimensionType.STRING)
        .meter(meter)
        .window(60_000L)
        .build();
  }

  @Test
  void shouldRejectMalformedTopKCountAtCompileTime() {
    // given a top-k meter whose k is not an integer
    final Meter meter = new Meter("top", MeterCatalog.TOP_K, "bpmnProcessId", Map.of("k", "abc"));

    // when compiled, then the bind failure surfaces as a validation error naming dataset, meter,
    // and param — not a bare NumberFormatException at topology-reload time
    assertThatThrownBy(() -> compiler.compile(1L, withMeter(meter)))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'param-check'")
        .hasMessageContaining("meter 'top'")
        .hasMessageContaining("param 'k'")
        .hasMessageContaining("'abc'");
  }

  @Test
  void shouldRejectUnknownRatioComparisonAtCompileTime() {
    // given a ratio meter with a nonsense comparison op
    final Meter meter = new Meter("sla", MeterCatalog.RATIO, "durationMs", Map.of("op", "banana"));

    // when compiled, then the error names the meter, the bad op, and the known values
    assertThatThrownBy(() -> compiler.compile(1L, withMeter(meter)))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'param-check'")
        .hasMessageContaining("meter 'sla'")
        .hasMessageContaining("'banana'")
        .hasMessageContaining("LE");
  }

  @Test
  void shouldRejectNonIncreasingHistogramThresholdsAtCompileTime() {
    // given histogram thresholds that are not strictly increasing
    final Meter meter =
        new Meter("hist", MeterCatalog.HISTOGRAM, "durationMs", Map.of("thresholds", "100,100"));

    // when compiled, then it is rejected naming the meter and the offending values
    assertThatThrownBy(() -> compiler.compile(1L, withMeter(meter)))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("meter 'hist'")
        .hasMessageContaining("strictly increasing");
  }

  @Test
  void shouldRejectMalformedHistogramThresholdsAtCompileTime() {
    // given histogram thresholds that do not parse as integers
    final Meter meter =
        new Meter("hist", MeterCatalog.HISTOGRAM, "durationMs", Map.of("thresholds", "a,b"));

    // when compiled, then the parse failure carries meter + param context
    assertThatThrownBy(() -> compiler.compile(1L, withMeter(meter)))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("meter 'hist'")
        .hasMessageContaining("param 'thresholds'");
  }

  @Test
  void shouldRejectUnknownMeterTypeAtCompileTime() {
    // when a meter's type id resolves to nothing, then compile rejects it with dataset context
    assertThatThrownBy(() -> compiler.compile(1L, withMeter(Meter.of("x", "no-such-type"))))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'param-check'")
        .hasMessageContaining("no-such-type");
  }

  @Test
  void shouldBindWellFormedParams() {
    // given valid params for the param-carrying meter kinds
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("param-ok", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(new Meter("top", MeterCatalog.TOP_K, "bpmnProcessId", Map.of("k", "5")))
            .meter(
                new Meter(
                    "sla",
                    MeterCatalog.RATIO,
                    "durationMs",
                    Map.of("op", "le", "threshold", "9000")))
            .meter(
                new Meter(
                    "hist", MeterCatalog.HISTOGRAM, "durationMs", Map.of("thresholds", "100,1000")))
            .window(60_000L)
            .build();

    // when / then all meters bind
    assertThat(compiler.compile(1L, declaration).meters()).hasSize(3);
  }

  @Test
  void shouldRejectNonNumericOrderingFilterAtCompileTime() {
    // given an ordering filter whose declared bound is not a number
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("filter-check", FactType.PROCESS_INSTANCE)
            .filterGreaterThan("durationMs", "fast")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .build();

    // when compiled, then it is rejected naming the dataset, the filter field, the operator, and
    // the offending value — not silently compiled into a filter that never matches
    assertThatThrownBy(() -> compiler.compile(1L, declaration))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'filter-check'")
        .hasMessageContaining("filter on 'durationMs'")
        .hasMessageContaining("GT")
        .hasMessageContaining("'fast'");
  }

  @Test
  void shouldRejectEmptyInListFilterAtCompileTime() {
    // given an IN filter whose list is empty after trimming
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("filter-check", FactType.PROCESS_INSTANCE)
            .filterIn("bpmnProcessId", " , ,")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .build();

    // when compiled, then it is rejected with the dataset + filter context
    assertThatThrownBy(() -> compiler.compile(1L, declaration))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'filter-check'")
        .hasMessageContaining("filter on 'bpmnProcessId'")
        .hasMessageContaining("IN")
        .hasMessageContaining("non-empty");
  }

  @Test
  void shouldRejectInvalidFilterOnProjectedTableAtCompileTime() {
    // given a projected table declaring the same bad ordering filter
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("row-check", FactType.PROCESS_INSTANCE)
            .asTable("processInstanceKey")
            .filterLessThan("durationMs", "slow")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .build();

    // when compiled via the table path, then the same admission gate rejects it
    assertThatThrownBy(() -> compiler.compileTable(1L, declaration))
        .isInstanceOf(DatasetValidationException.class)
        .hasMessageContaining("dataset 'row-check'")
        .hasMessageContaining("filter on 'durationMs'")
        .hasMessageContaining("LT");
  }

  @Test
  void shouldCompileWellFormedOperatorFilters() {
    // given one filter per operator, each with a well-formed value
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("filter-ok", FactType.PROCESS_INSTANCE)
            .filterEquals("bpmnProcessId", "invoice")
            .filterNotEquals("state", "CANCELED")
            .filterLessThan("durationMs", "9000")
            .filterLessOrEqual("durationMs", "9000")
            .filterGreaterThan("durationMs", "1.5")
            .filterGreaterOrEqual("durationMs", "-7")
            .filterIn("tenantId", "a, b,c")
            .filterIsNull("incidentKey")
            .filterNotNull("processDefinitionKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .build();

    // when / then it compiles, carrying every filter into the fact binding
    assertThat(compiler.compile(1L, declaration).factBinding().filters()).hasSize(9);
  }

  @Test
  void shouldAssignStableStreamAndCellGroupIdsAcrossRecompile() {
    // given a registry shared across two compiler instances (a restart)
    final InMemoryMeterIdStore store = new InMemoryMeterIdStore();
    final CompiledDataset first =
        new DatasetCompiler(MeterCatalog.withDefaults(), new MeterIdRegistry(store))
            .compile(1L, declaration());

    // when recompiled against the same id store
    final CompiledDataset second =
        new DatasetCompiler(MeterCatalog.withDefaults(), new MeterIdRegistry(store))
            .compile(1L, declaration());

    // then the stream and cell-group ids are identical (stable)
    assertThat(second.streamId()).isEqualTo(first.streamId());
    assertThat(second.tiers())
        .extracting(CompiledTier::cellGroup)
        .containsExactlyElementsOf(first.tiers().stream().map(CompiledTier::cellGroup).toList());
  }
}
