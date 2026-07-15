/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.ElementOutlier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The per-flow-node duration-outlier read: the boxplot fence ({@code Q3 + 1.5 * IQR}) computed from
 * the elements cube's {@code duration_p} sketch, sorted by outlier count, with a minimum
 * observation guard and a never-500 guard for a metadata store predating the meter.
 */
final class DashboardOutliersServingTest {

  private static final String PROCESS = "claim-process";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldRankTheHotElementFirstWithASaneFenceAndShare() {
    // given a "worker" element with 25 fast completions (900..1200ms) plus 5 far outliers at
    // 100_000ms — well above MIN_OBSERVATIONS (20) so its fence is trusted
    final List<Fact> workerFacts = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      workerFacts.add(completed(900L + i * 12L));
    }
    for (int i = 0; i < 5; i++) {
      workerFacts.add(completed(100_000L));
    }
    ServingTestSupport.seed(fixture, "elements", "duration_p", workerFacts, PROCESS, "worker");
    // and a "quiet" element with only 10 completions — below MIN_OBSERVATIONS, must be excluded
    final List<Fact> quietFacts = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      quietFacts.add(completed(1_000L));
    }
    ServingTestSupport.seed(fixture, "elements", "duration_p", quietFacts, PROCESS, "quiet");

    // when the outliers are read
    final List<ElementOutlier> outliers = repository.elementOutliers(PROCESS, null, null);

    // then only the element with enough observations serves, with a fence below the outlier tail
    // and a share/count reflecting roughly the 5-of-30 outlier fraction
    assertThat(outliers)
        .singleElement()
        .satisfies(o -> assertThat(o.elementId()).isEqualTo("worker"));
    final ElementOutlier worker = outliers.get(0);
    assertThat(worker.n()).isEqualTo(30L);
    assertThat(worker.fenceMs()).isLessThan(100_000L);
    assertThat(worker.share()).isGreaterThan(0.0);
    assertThat(worker.count()).isGreaterThan(0L);
  }

  @Test
  void shouldExcludeAnElementRightAtTheObservationBoundary() {
    // given exactly 20 observations — MIN_OBSERVATIONS is a floor, and 19 must still be excluded
    final List<Fact> nineteen = new ArrayList<>();
    for (int i = 0; i < 19; i++) {
      nineteen.add(completed(1_000L + i));
    }
    ServingTestSupport.seed(fixture, "elements", "duration_p", nineteen, PROCESS, "borderline");

    // when read
    final List<ElementOutlier> outliers = repository.elementOutliers(PROCESS, null, null);

    // then the element stays hidden below the trust threshold
    assertThat(outliers).isEmpty();
  }

  @Test
  void shouldServeAnEmptyListWhenTheStoreHasNoDurationPMeter() {
    // given a catalog whose "elements" cube is missing entirely (a metadata store bootstrapped
    // before duration_p existed) — never a 500 that would blank the whole dashboard render
    final DashboardRepository withoutElements =
        new DashboardRepository(
            fixture.executor(),
            new DatasetCatalog(Map.<String, CompiledDataset>of()),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));

    // when / then
    assertThat(withoutElements.elementOutliers(PROCESS, null, null)).isEmpty();
  }

  @Test
  void shouldServeNoOutliersForAnUnknownProcess() {
    assertThat(repository.elementOutliers("unknown", null, null)).isEmpty();
  }

  private static Fact completed(final long durationMs) {
    return Fact.builder(FactType.ELEMENT)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }
}
