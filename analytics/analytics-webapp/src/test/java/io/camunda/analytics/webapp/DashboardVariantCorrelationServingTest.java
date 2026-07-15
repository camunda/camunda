/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.VariantCorrelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The variant-correlation read: for each declared driver variable's {@code corr-variant-*} cube,
 * which value most predicts a given execution variant (by lift), one row per (variant, variable).
 */
final class DashboardVariantCorrelationServingTest {

  private static final String PROCESS = "claim-process";
  private static final long HAPPY = 111L;
  private static final long LOOP = 222L;

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
  void shouldPickTheStrongestDriverPerVariant() {
    // given joint counts: n(HAPPY,auto)=80, n(HAPPY,manual)=10, n(LOOP,auto)=10, n(LOOP,manual)=90
    // — hand-computed lift: P(HAPPY)=90/190=0.474, P(HAPPY|auto)=80/90=0.889 -> lift ~1.88 (top for
    // HAPPY); P(LOOP)=100/190=0.526, P(LOOP|manual)=90/100=0.9 -> lift ~1.71 (top for LOOP)
    seedVariantRoute(HAPPY, "auto", 80);
    seedVariantRoute(HAPPY, "manual", 10);
    seedVariantRoute(LOOP, "auto", 10);
    seedVariantRoute(LOOP, "manual", 90);

    // when the correlation reads
    final List<VariantCorrelation> correlations =
        repository.variantCorrelations(PROCESS, null, null);

    // then each variant's top driver matches the hand-computed lift
    assertThat(correlations).hasSize(2);
    final VariantCorrelation happy =
        correlations.stream()
            .filter(c -> c.variantHash().equals(String.valueOf(HAPPY)))
            .findFirst()
            .orElseThrow();
    assertThat(happy.variable()).isEqualTo("route");
    assertThat(happy.value()).isEqualTo("auto");
    assertThat(happy.n()).isEqualTo(80L);
    assertThat(happy.lift()).isCloseTo(1.88, within(0.05));
    final VariantCorrelation loop =
        correlations.stream()
            .filter(c -> c.variantHash().equals(String.valueOf(LOOP)))
            .findFirst()
            .orElseThrow();
    assertThat(loop.variable()).isEqualTo("route");
    assertThat(loop.value()).isEqualTo("manual");
    assertThat(loop.n()).isEqualTo(90L);
    assertThat(loop.lift()).isCloseTo(1.71, within(0.05));
  }

  @Test
  void shouldExcludeAPairBelowTheMinimumSupport() {
    // given a variant whose only value pairing has fewer than MIN_SUPPORT (10) joint observations
    seedVariantRoute(HAPPY, "auto", 80);
    seedVariantRoute(HAPPY, "manual", 10);
    seedVariantRoute(LOOP, "manual", 5); // below MIN_SUPPORT

    // when read
    final List<VariantCorrelation> correlations =
        repository.variantCorrelations(PROCESS, null, null);

    // then the sparse variant never appears
    assertThat(correlations)
        .extracting(VariantCorrelation::variantHash)
        .doesNotContain(String.valueOf(LOOP));
  }

  @Test
  void shouldServeEmptyWhenThereIsNoVariantCorrCube() {
    // given a catalog with no corr-variant-* cube provisioned
    final CompiledDataset processVariants = fixture.catalog().require("process-variants");
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    byName.put(processVariants.name(), processVariants);
    final DashboardRepository withoutCorrCubes =
        new DashboardRepository(
            fixture.executor(),
            new DatasetCatalog(byName),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));

    // when / then — never a 500, just an empty read
    assertThat(withoutCorrCubes.variantCorrelations(PROCESS, null, null)).isEmpty();
  }

  private void seedVariantRoute(final long variantHash, final String route, final int count) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("variantHash", variantHash)
              .field("var.route", route)
              .build());
    }
    ServingTestSupport.seed(
        fixture, "corr-variant-route", "count", facts, PROCESS, variantHash, route);
  }
}
