/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.DatasetQueryPlanner;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardOverview;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The combined-render read path: one {@link DashboardRepository#overview} call answers every widget
 * against a single per-render memo, so a query shared by several widgets hits the serving store
 * once — without any cross-render caching (each call starts from a fresh memo).
 */
final class DashboardOverviewServingTest {

  private static final String PROCESS = "order-process";
  private static final String TENANT = "<default>";

  private Fixture fixture;
  private CountingQueryClient counting;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    counting = new CountingQueryClient(fixture.datasetStore().queryClient());
    repository =
        new DashboardRepository(
            new DatasetQueryExecutor(new DatasetQueryPlanner(), counting),
            fixture.catalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()));
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldRunSharedQueriesOncePerOverviewRender() {
    // given seeded duration and SLA cubes
    ServingTestSupport.seed(
        fixture, "process-duration", "p95", completed(100_000L, 200_000L), PROCESS);
    ServingTestSupport.seed(
        fixture, "process-sla", "sla_compliance", completed(100_000L, 400_000L), PROCESS);

    // and the store cost of each shared query when it runs exactly once
    counting.reset();
    repository.durationBuckets(PROCESS, null, null);
    final int oneLifecycleSeries = counting.count("process-instances");
    counting.reset();
    repository.activatedInstances(PROCESS, null, null);
    final int oneLifecycleTotal = counting.count("process-instances");
    counting.reset();
    repository.ratios(PROCESS, "sla_compliance", null, null);
    final int oneSlaSeries = counting.count("process-sla");

    // when one overview render runs
    counting.reset();
    final DashboardOverview overview = repository.overview(PROCESS, TENANT, null, null);
    final int lifecycleFetches = counting.count("process-instances");
    final int slaFetches = counting.count("process-sla");

    // then the lifecycle series (feeding slaCohorts, noIncidentCohorts and durationBuckets) and
    // the lifecycle total (feeding activated and activeNow) each ran once, and the SLA ratio
    // series (feeding its own widget and the cohort join) ran once
    assertThat(lifecycleFetches).isEqualTo(oneLifecycleSeries + oneLifecycleTotal);
    assertThat(slaFetches).isEqualTo(oneSlaSeries);

    // and the deduped render returns exactly what the per-widget endpoints return
    assertThat(overview.summary()).isEqualTo(repository.durationSummary(PROCESS, null, null));
    assertThat(overview.duration()).isEqualTo(repository.durationPercentiles(PROCESS, null, null));
    assertThat(overview.sla()).isEqualTo(repository.ratios(PROCESS, "sla_compliance", null, null));
    assertThat(overview.slaCohorts()).isEqualTo(repository.slaCohorts(PROCESS, null, null));
    assertThat(overview.noIncidentCohorts())
        .isEqualTo(repository.noIncidentCohorts(PROCESS, null, null));
    assertThat(overview.durationBuckets())
        .isEqualTo(repository.durationBuckets(PROCESS, null, null));
    assertThat(overview.activated()).isEqualTo(repository.activatedInstances(PROCESS, null, null));
    assertThat(overview.activeNow()).isEqualTo(repository.activeInstances(PROCESS, TENANT));
    assertThat(overview.openIncidents()).isEqualTo(repository.openIncidents(PROCESS));
  }

  private static List<Fact> completed(final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durationsMs) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .build());
    }
    return facts;
  }

  /** Counts store reads per dataset name, delegating everything to the real client. */
  private static final class CountingQueryClient implements DatasetQueryClient {

    private final DatasetQueryClient delegate;
    private final Map<String, Integer> byDataset = new HashMap<>();

    private CountingQueryClient(final DatasetQueryClient delegate) {
      this.delegate = delegate;
    }

    int count(final String dataset) {
      return byDataset.getOrDefault(dataset, 0);
    }

    void reset() {
      byDataset.clear();
    }

    @Override
    public List<Cell> fetch(final DatasetFetch fetch) {
      byDataset.merge(fetch.dataset().name(), 1, Integer::sum);
      return delegate.fetch(fetch);
    }

    @Override
    public void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
      byDataset.merge(fetch.dataset().name(), 1, Integer::sum);
      delegate.streamCells(fetch, sink);
    }

    @Override
    public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
      byDataset.merge(fetch.dataset().name(), 1, Integer::sum);
      return delegate.fetchAggregated(fetch);
    }

    @Override
    public List<TableRow> fetchRows(final TableFetch fetch) {
      return delegate.fetchRows(fetch);
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
