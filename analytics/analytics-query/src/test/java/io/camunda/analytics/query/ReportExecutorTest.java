/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.report.Combination;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.report.ReportSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ReportExecutorTest {

  private final DatasetCompiler compiler =
      new DatasetCompiler(
          MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));
  private final CompiledDataset throughput = cube(1L, "throughput");
  private final CompiledDataset incidents = cube(2L, "incidents");

  private CompiledDataset cube(final long cubeId, final String name) {
    return compiler.compile(
        cubeId,
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .build());
  }

  private ReportExecutor executorReturning(final Map<String, List<ReportRow>> rowsByDataset) {
    final ReportExecutor.SourceQuery sourceQuery =
        (query, dataset) -> new ReportResult(rowsByDataset.getOrDefault(dataset.name(), List.of()));
    return new ReportExecutor(
        sourceQuery,
        name ->
            switch (name) {
              case "throughput" -> throughput;
              case "incidents" -> incidents;
              default -> null;
            });
  }

  private static ReportRow row(final String process, final long count) {
    return new ReportRow(Map.of("bpmnProcessId", process), 0L, Map.of("count", count));
  }

  private static ReportDefinition unionOf(final ReportSource... sources) {
    return new ReportDefinition(
        1L, "r", List.of(sources), List.of("bpmnProcessId"), 60_000L, Combination.UNION, "table");
  }

  @Test
  void shouldUnionSourcesOnSharedKeyNamespacingMetersByDataset() {
    // given two datasets, sharing the "invoice" group key and each with a unique key
    final ReportExecutor executor =
        executorReturning(
            Map.of(
                "throughput", List.of(row("invoice", 5L), row("order", 3L)),
                "incidents", List.of(row("invoice", 2L), row("refund", 1L))));

    // when a union report reads "count" from both
    final ReportResult result =
        executor.execute(
            unionOf(
                new ReportSource("throughput", List.of("count"), List.of()),
                new ReportSource("incidents", List.of("count"), List.of())),
            0L,
            60_000L);

    // then same-named meters are namespaced by dataset and merged on the shared key
    assertThat(result.rows())
        .extracting(ReportRow::dimensions, ReportRow::measures)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                Map.of("bpmnProcessId", "invoice"),
                Map.of("throughput.count", 5L, "incidents.count", 2L)),
            org.assertj.core.groups.Tuple.tuple(
                Map.of("bpmnProcessId", "order"), Map.of("throughput.count", 3L)),
            org.assertj.core.groups.Tuple.tuple(
                Map.of("bpmnProcessId", "refund"), Map.of("incidents.count", 1L)));
  }

  @Test
  void shouldRejectJoinCombination() {
    final ReportExecutor executor = executorReturning(Map.of());
    final ReportDefinition join =
        new ReportDefinition(
            1L,
            "r",
            List.of(new ReportSource("throughput", List.of("count"), List.of())),
            List.of("bpmnProcessId"),
            60_000L,
            Combination.JOIN,
            "table");

    assertThatThrownBy(() -> executor.execute(join, 0L, 60_000L))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("UNION");
  }

  @Test
  void shouldRejectUnknownDataset() {
    final ReportExecutor executor = executorReturning(Map.of());

    assertThatThrownBy(
            () ->
                executor.execute(
                    unionOf(new ReportSource("missing", List.of("count"), List.of())), 0L, 60_000L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
  }
}
