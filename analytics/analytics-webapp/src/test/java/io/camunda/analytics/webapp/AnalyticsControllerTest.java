/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.query.ReportResult;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.catalog.DatasetProvisioningService;
import io.camunda.analytics.serving.catalog.StandardDatasets;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The declare-and-read API's question flow, against the H2-backed serving stack: a question with
 * {@code compare} filters reads the same dataset twice (baseline + filtered slice) and the executor
 * namespaces the repeat as {@code dataset#2.meter}.
 */
final class AnalyticsControllerTest {

  private Fixture fixture;
  private AnalyticsController controller;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    final DatasetProvisioningService provisioningService =
        new DatasetProvisioningService(
            fixture.metadataStore(),
            fixture.datasetStore().schemaManager(),
            MeterCatalog.withDefaults(),
            System::currentTimeMillis,
            0L);
    controller =
        new AnalyticsController(
            provisioningService,
            fixture.metadataStore(),
            fixture.executor(),
            new MeasureCatalog(),
            // The heatmap and raw-table endpoints are not under test here.
            null,
            null);
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldCreateACompareReportReadingTheSameDatasetTwice() {
    // when a question carries a compare filter
    final ReportDefinition report =
        controller.createReportFromQuestion(
            new AnalyticsController.QuestionRequest(
                "EU vs all",
                "process-instances",
                "instance-count",
                Map.of(),
                List.of(),
                List.of(),
                List.of(new MeasureCatalog.QuestionFilter("tenantId", "eu")),
                60_000L,
                "line"));

    // then the report reads one dataset twice: the unfiltered baseline plus the filtered slice
    assertThat(report.sources()).hasSize(2);
    assertThat(report.sources().get(0).datasetName())
        .isEqualTo(report.sources().get(1).datasetName());
    assertThat(report.sources().get(0).filters()).isEmpty();
    assertThat(report.sources().get(1).filters())
        .containsExactly(FilterPredicate.equals("tenantId", "eu"));
    // ... the compare field became a grain dimension (filterable) but not a report group-by
    assertThat(report.groupBy()).isEmpty();
    final CompiledDataset derived = compiledByName(report.sources().get(0).datasetName());
    assertThat(derived.grain().indexOf("tenantId")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void shouldServeCompareMeasuresUnderOrdinalNamespaces() {
    // given a saved compare report and seeded cells for two tenants in one window
    final ReportDefinition report =
        controller.createReportFromQuestion(
            new AnalyticsController.QuestionRequest(
                "EU vs all",
                "process-instances",
                "instance-count",
                Map.of(),
                List.of(),
                List.of(),
                List.of(new MeasureCatalog.QuestionFilter("tenantId", "eu")),
                60_000L,
                "line"));
    final String datasetName = report.sources().get(0).datasetName();
    final CompiledDataset derived = compiledByName(datasetName);
    final long windowStart = ServingTestSupport.window(60_000L);
    seedCell(derived, windowStart, facts(2), "eu");
    seedCell(derived, windowStart, facts(3), "us");

    // when the report runs over the seeded window
    final ReportResult result =
        controller.reportData(report.reportId(), 0L, windowStart + 60_000L).getBody();

    // then one row carries the baseline under the plain namespace and the slice under #2
    assertThat(result).isNotNull();
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(((Number) row.measures().get(datasetName + ".count")).longValue())
                  .isEqualTo(5L);
              assertThat(((Number) row.measures().get(datasetName + "#2.count")).longValue())
                  .isEqualTo(2L);
            });
  }

  private CompiledDataset compiledByName(final String name) {
    for (final ActiveCube cube : StandardDatasets.loadCubes(fixture.metadataStore())) {
      if (cube.compiled().name().equals(name)) {
        return cube.compiled();
      }
    }
    throw new AssertionError("dataset '" + name + "' was not provisioned");
  }

  private void seedCell(
      final CompiledDataset dataset,
      final long windowStart,
      final List<Fact> facts,
      final Object... keyValues) {
    fixture
        .datasetStore()
        .writer()
        .upsertCell(
            dataset,
            DimensionKey.of(dataset.grain(), keyValues),
            windowStart,
            dataset.finestTier().windowMs(),
            ServingTestSupport.fold(dataset, facts),
            WriteVersion.SEED);
    fixture.datasetStore().writer().flush();
  }

  private static List<Fact> facts(final int count) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).build());
    }
    return facts;
  }
}
