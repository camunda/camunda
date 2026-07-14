/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.SlaCohortPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The audited cohort-join traps: the SLA/no-incident cohorts join two datasets' series on raw
 * {@code windowStart}, which only aligns when both sides are bucketed at one common granularity —
 * and a meter name must resolve to exactly one owning dataset, never a silent first match.
 */
final class DashboardCohortJoinTest {

  private static final String PROCESS = "order-process";
  private static final long MINUTE = 60_000L;
  private static final long TWO_MINUTES = 2 * MINUTE;

  private Fixture fixture;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldJoinCohortsAcrossDatasetsWithDifferentFinestTiers() {
    // given a catalog whose SLA ratio cube stores 2-minute windows while the lifecycle cube
    // stores 1-minute windows (the trap: joining each side at its own tier misses every key)
    final CompiledDataset lifecycle = fixture.catalog().require("process-instances");
    final CompiledDataset coarseSla =
        compile(
            900L,
            DatasetDeclaration.builder("process-sla-coarse", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", Transition.COMPLETED.name())
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(
                    new Meter(
                        "sla_compliance",
                        MeterCatalog.RATIO,
                        "durationMs",
                        Map.of("op", "le", "threshold", "9000")))
                .window(TWO_MINUTES)
                .build());
    final DashboardRepository repository = repository(lifecycle, coarseSla);

    // ... two adjacent lifecycle minutes inside one 2-minute SLA window: 3 + 2 instances started,
    // 2 + 1 completed (4s, 12s | 5s), and the SLA cube's single 2-minute cell (2 of 3 within 9s)
    final long twoMinuteWindow = ServingTestSupport.window(TWO_MINUTES);
    seedCell(lifecycle, twoMinuteWindow, MINUTE, lifecycleFacts(3, 4_000L, 12_000L), PROCESS);
    seedCell(lifecycle, twoMinuteWindow + MINUTE, MINUTE, lifecycleFacts(2, 5_000L), PROCESS);
    seedCell(coarseSla, twoMinuteWindow, TWO_MINUTES, completed(4_000L, 12_000L, 5_000L), PROCESS);

    // when the cohorts are read
    final List<SlaCohortPoint> cohorts = repository.slaCohorts(PROCESS, null, null);

    // then both sides were bucketed at the common 2-minute granularity and joined into one
    // cohort: 5 started, 2 met (settled within SLA), 2 still open, 1 breached — instead of the
    // per-minute cohorts silently reporting met = 0 because no 1-minute key matched a 2-minute one
    assertThat(cohorts)
        .singleElement()
        .satisfies(
            cohort -> {
              assertThat(cohort.windowStart()).isEqualTo(twoMinuteWindow);
              assertThat(cohort.started()).isEqualTo(5L);
              assertThat(cohort.met()).isEqualTo(2L);
              assertThat(cohort.open()).isEqualTo(2L);
              assertThat(cohort.breached()).isEqualTo(1L);
            });
  }

  @Test
  void shouldRejectAMeterNameOwnedByMoreThanOneDataset() {
    // given a catalog where two datasets both declare a meter named sla_compliance
    final CompiledDataset standardSla = fixture.catalog().require("process-sla");
    final CompiledDataset duplicate =
        compile(
            901L,
            DatasetDeclaration.builder("process-sla-strict", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", Transition.COMPLETED.name())
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(
                    new Meter(
                        "sla_compliance",
                        MeterCatalog.RATIO,
                        "durationMs",
                        Map.of("op", "le", "threshold", "1000")))
                .window(MINUTE)
                .build());
    final DashboardRepository repository = repository(standardSla, duplicate);

    // when / then — resolving by meter name fails loudly instead of picking the first match
    assertThatThrownBy(() -> repository.ratios(PROCESS, "sla_compliance", null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sla_compliance")
        .hasMessageContaining("more than one dataset");
  }

  /** Compiles a test-local declaration and provisions its serving table on the fixture store. */
  private CompiledDataset compile(final long cubeId, final DatasetDeclaration declaration) {
    final CompiledDataset compiled =
        new DatasetCompiler(
                MeterCatalog.withDefaults(),
                new MeterIdRegistry(fixture.metadataStore().meterIdStore()))
            .compile(cubeId, declaration);
    fixture.datasetStore().schemaManager().ensure(compiled);
    return compiled;
  }

  /** A repository over exactly the given cubes — no other dataset can shadow a meter name. */
  private DashboardRepository repository(final CompiledDataset... cubes) {
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    for (final CompiledDataset cube : cubes) {
      byName.put(cube.name(), cube);
    }
    return new DashboardRepository(
        fixture.executor(),
        new DatasetCatalog(byName),
        new TableQueryExecutor(fixture.datasetStore().queryClient()));
  }

  /** Seeds one cube cell at an explicit window, folding the facts through every meter slot. */
  private void seedCell(
      final CompiledDataset dataset,
      final long windowStart,
      final long windowMs,
      final List<Fact> facts,
      final Object... keyValues) {
    fixture
        .datasetStore()
        .writer()
        .upsertCell(
            dataset,
            DimensionKey.of(dataset.grain(), keyValues),
            windowStart,
            windowMs,
            ServingTestSupport.fold(dataset, facts),
            WriteVersion.SEED);
    fixture.datasetStore().writer().flush();
  }

  /** {@code started} activations plus one completion per given duration. */
  private static List<Fact> lifecycleFacts(final int started, final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < started; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.ACTIVATED).build());
    }
    facts.addAll(completed(durationsMs));
    return facts;
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
