/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.Offset.offset;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.ActiveInstancesPoint;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.DistinctPoint;
import io.camunda.analytics.webapp.dashboard.DurationBucketPoint;
import io.camunda.analytics.webapp.dashboard.DurationPercentilePoint;
import io.camunda.analytics.webapp.dashboard.DurationSpreadPoint;
import io.camunda.analytics.webapp.dashboard.RatioPoint;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Read-path tests: the dashboard queries answered through the neutral serving executor. */
final class DashboardServingTest {

  private static final String PROCESS = "order-process";
  private static final String TENANT = "<default>";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldReturnDurationSummaryFromServingStore() {
    // given four completed instances with known durations folded into the percentile cube
    ServingTestSupport.seed(
        fixture,
        "process-duration",
        "p95",
        completed(100_000L, 200_000L, 300_000L, 400_000L),
        PROCESS);

    // when the KPI-tile summary reads the whole range
    final DurationPercentilePoint summary = repository.durationSummary(PROCESS, null, null);

    // then count / min / max are exact and the percentiles fall inside the observed range
    assertThat(summary.observationCount()).isEqualTo(4L);
    assertThat(summary.minMs()).isEqualTo(100_000L);
    assertThat(summary.maxMs()).isEqualTo(400_000L);
    assertThat(summary.p50Ms()).isBetween(100_000L, 400_000L);
    assertThat(summary.p90Ms()).isGreaterThanOrEqualTo(summary.p50Ms());
  }

  @Test
  void shouldReturnSlaRatioFromServingStore() {
    // given four completed instances, three within the default 9s SLA threshold (durationMs <= 9s)
    final long window =
        ServingTestSupport.seed(
            fixture,
            "process-sla",
            "sla_compliance",
            completed(4_000L, 6_000L, 9_000L, 12_000L),
            PROCESS);

    // when the SLA-compliance ratio series is read (by its declared meter name)
    final List<RatioPoint> ratios = repository.ratios(PROCESS, "sla_compliance", null, null);

    // then it is matched=3 of total=4 for the seeded window
    assertThat(ratios)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.windowStart()).isEqualTo(window);
              assertThat(point.matched()).isEqualTo(3L);
              assertThat(point.total()).isEqualTo(4L);
              assertThat(point.ratio()).isEqualTo(0.75);
              assertThat(point.maturing()).isFalse();
            });
  }

  @Test
  void shouldReturnUnmodeledRatioAsEmpty() {
    // given a seeded SLA cube but a meter name no declared dataset owns
    ServingTestSupport.seed(fixture, "process-sla", "sla_compliance", completed(100_000L), PROCESS);

    // when / then — no dataset declares this meter, so it reads empty
    assertThat(repository.ratios(PROCESS, "unmodeled_ratio", null, null)).isEmpty();
  }

  @Test
  void shouldReturnDistinctCountFromServingStore() {
    // given three distinct process definitions observed for the tenant
    final List<Fact> facts = new ArrayList<>();
    for (final String process : List.of("order-process", "payment-process", "shipping-process")) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).field("bpmnProcessId", process).build());
    }
    final long window =
        ServingTestSupport.seed(fixture, "process-distinct", "distinct", facts, TENANT);

    // when the distinct series is read
    final List<DistinctPoint> distinct = repository.distinct(TENANT, null, null);

    // then the estimate is three, inside its confidence interval, for the seeded window
    assertThat(distinct)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.windowStart()).isEqualTo(window);
              assertThat(point.estimate()).isEqualTo(3L);
              assertThat(point.lowerBound()).isLessThanOrEqualTo(3L);
              assertThat(point.upperBound()).isGreaterThanOrEqualTo(3L);
            });
  }

  @Test
  void shouldServeTheActiveSeriesFromSnapshotsAndTheSpreadFromPrimitives() {
    // given active-instances snapshots (3 running at 1m, 5 at 3m) for the process
    final long minute = 60_000L;
    final CompiledDataset active = fixture.catalog().require("active-instances");
    final DimensionKey key = DimensionKey.of(active.grain(), PROCESS);
    fixture
        .datasetStore()
        .writer()
        .upsertSnapshotRow(active, key, minute, level(active, 3L), new WriteVersion(1, 1));
    fixture
        .datasetStore()
        .writer()
        .upsertSnapshotRow(active, key, 3 * minute, level(active, 5L), new WriteVersion(1, 2));
    // and one window of completions (100s/200s/300s) in the spread cube
    ServingTestSupport.seed(
        fixture,
        "process-duration-spread",
        "stddev",
        completed(100_000L, 200_000L, 300_000L),
        PROCESS);

    // when both widgets read
    final List<ActiveInstancesPoint> series = repository.activeSeries(PROCESS, 0L, 4 * minute);
    final List<DurationSpreadPoint> spread = repository.durationSpread(PROCESS, null, null);

    // then the snapshots carry forward through the silent buckets (absolute values)
    assertThat(series)
        .extracting(ActiveInstancesPoint::time, ActiveInstancesPoint::active)
        .containsExactly(
            tuple(minute, 3L), tuple(2 * minute, 3L), tuple(3 * minute, 5L), tuple(4 * minute, 5L));
    // and the spread window carries the exact extrema and the population stddev of the durations
    assertThat(spread).hasSize(1);
    assertThat(spread.get(0).minMs()).isEqualTo(100_000L);
    assertThat(spread.get(0).maxMs()).isEqualTo(300_000L);
    assertThat(spread.get(0).stddevMs()).isCloseTo(81_649.66, offset(0.1));
  }

  /** The active-instances cube's single LEVEL slot as composite accumulator bytes. */
  private static byte[] level(final CompiledDataset dataset, final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds())
        .toBytes(new Object[] {new MutableLong(value)});
  }

  @Test
  void shouldDeriveLifecycleWidgetsFromThePrimitiveMeters() {
    // given a process-instances window with 5 started, 3 completed (2s/20s/70s), 1 terminated
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.ACTIVATED).build());
    }
    facts.addAll(completed(2_000L, 20_000L, 70_000L));
    facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.TERMINATED).build());
    final long window =
        ServingTestSupport.seed(fixture, "process-instances", "activated", facts, PROCESS);

    // when the lifecycle-derived widgets read the cube
    final long activated = repository.activatedInstances(PROCESS, null, null);
    final long activeNow = repository.activeInstances(PROCESS, TENANT);
    final List<DurationBucketPoint> buckets = repository.durationBuckets(PROCESS, null, null);

    // then the per-transition counts compose the same numbers the bundle used to report
    assertThat(activated).isEqualTo(5L);
    assertThat(activeNow).isEqualTo(1L); // 5 started − 3 completed − 1 terminated
    // and the overview's Ended tile counts completed AND terminated — reading the duration
    // summary instead (the COMPLETED-filtered cube) would silently drop the terminated instance
    // from the books: started − ended would no longer reconcile with in-progress
    assertThat(repository.overview(PROCESS, TENANT, null, null).ended()).isEqualTo(4L);
    assertThat(buckets)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.windowStart()).isEqualTo(window);
              assertThat(point.started()).isEqualTo(5L);
              assertThat(point.open()).isEqualTo(1L);
              // exact histogram bands [<10s, <30s, <60s, <120s, ≥120s] over the completions only
              assertThat(point.bands()).containsExactly(1L, 1L, 0L, 1L, 0L);
            });
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
}
