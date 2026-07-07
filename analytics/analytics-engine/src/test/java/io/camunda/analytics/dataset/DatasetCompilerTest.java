/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
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
    assertThat(compiled.keySelector().getKey(fact))
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
  void shouldMaterialiseOneMeterPerTierWithDistinctStableAggIds() {
    // when
    final CompiledDataset compiled = compiler.compile(1L, declaration());

    // then two meters x two tiers = four compiled meters, each with a distinct aggId
    assertThat(compiled.meters()).hasSize(4);
    assertThat(compiled.meters()).extracting(CompiledMeter::aggId).doesNotHaveDuplicates();
    assertThat(compiled.meters())
        .extracting(CompiledMeter::meterName, CompiledMeter::windowMs)
        .containsExactlyInAnyOrder(
            tuple("duration", 60_000L),
            tuple("duration", 3_600_000L),
            tuple("count", 60_000L),
            tuple("count", 3_600_000L));
  }

  @Test
  void shouldAssignStableAggIdsAcrossRecompile() {
    // given a registry shared across two compiler instances (a restart)
    final InMemoryMeterIdStore store = new InMemoryMeterIdStore();
    final CompiledDataset first =
        new DatasetCompiler(MeterCatalog.withDefaults(), new MeterIdRegistry(store))
            .compile(1L, declaration());

    // when recompiled against the same id store
    final CompiledDataset second =
        new DatasetCompiler(MeterCatalog.withDefaults(), new MeterIdRegistry(store))
            .compile(1L, declaration());

    // then the aggIds are identical (stable), meter-for-meter
    assertThat(second.meters())
        .extracting(CompiledMeter::aggId)
        .containsExactlyElementsOf(first.meters().stream().map(CompiledMeter::aggId).toList());
  }
}
