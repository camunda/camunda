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
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.VariantRow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The top-variants read: the process-variants cube's whole-range totals grouped by the LONG {@code
 * variantHash} dimension, joined with the variant-catalog dictionary — the round trip of the cube
 * and the table plus the endpoint's share math.
 */
final class DashboardVariantsServingTest {

  private static final String PROCESS = "claim-process";
  private static final long HAPPY_PATH = 111L;
  private static final long RETRY_LOOP = -222L; // hashes are raw XOR folds — negatives are normal

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
  void shouldRankVariantsByCountWithSharesAndDictionaryJoin() {
    // given two variants in one window: the happy path (3 ended) and a retry loop (1 ended)
    ServingTestSupport.seed(
        fixture, "process-variants", "count", ended(1_000L, 2_000L, 3_000L), PROCESS, HAPPY_PATH);
    ServingTestSupport.seed(
        fixture, "process-variants", "count", ended(10_000L), PROCESS, RETRY_LOOP);
    // and their dictionary rows
    seedCatalogRow(HAPPY_PATH, "assess-auto, payout, register");
    seedCatalogRow(RETRY_LOOP, "assess-manual×2-3, payout, register");

    // when the top variants are read
    final List<VariantRow> variants = repository.variants(PROCESS, null, null, 10);

    // then they rank by count, shares are against all ended-with-variant instances, and the
    // element chains join in from the catalog
    assertThat(variants).hasSize(2);
    final VariantRow top = variants.get(0);
    assertThat(top.variantHash()).isEqualTo(String.valueOf(HAPPY_PATH));
    assertThat(top.count()).isEqualTo(3L);
    assertThat(top.share()).isEqualTo(0.75);
    assertThat(top.elements()).isEqualTo("assess-auto, payout, register");
    assertThat(top.p50Ms()).isBetween(1_000L, 3_000L);
    assertThat(top.p95Ms()).isGreaterThanOrEqualTo(top.p50Ms());
    final VariantRow second = variants.get(1);
    assertThat(second.variantHash()).isEqualTo(String.valueOf(RETRY_LOOP));
    assertThat(second.count()).isEqualTo(1L);
    assertThat(second.share()).isEqualTo(0.25);
    assertThat(second.elements()).isEqualTo("assess-manual×2-3, payout, register");
  }

  @Test
  void shouldJoinAMissingDictionaryRowAsEmptyElements() {
    // given a cube row whose dictionary row has not landed yet (the two are written independently)
    ServingTestSupport.seed(
        fixture, "process-variants", "count", ended(5_000L), PROCESS, HAPPY_PATH);

    // when the top variants are read
    final List<VariantRow> variants = repository.variants(PROCESS, null, null, 10);

    // then the variant still serves, with an empty chain instead of being dropped
    assertThat(variants)
        .singleElement()
        .satisfies(
            variant -> {
              assertThat(variant.variantHash()).isEqualTo(String.valueOf(HAPPY_PATH));
              assertThat(variant.elements()).isEmpty();
              assertThat(variant.share()).isEqualTo(1.0);
            });
  }

  @Test
  void shouldTruncateToTheRequestedLimit() {
    // given three variants with distinct counts
    ServingTestSupport.seed(
        fixture, "process-variants", "count", ended(1_000L, 1_000L, 1_000L), PROCESS, 1L);
    ServingTestSupport.seed(
        fixture, "process-variants", "count", ended(1_000L, 1_000L), PROCESS, 2L);
    ServingTestSupport.seed(fixture, "process-variants", "count", ended(1_000L), PROCESS, 3L);

    // when only the top two are requested
    final List<VariantRow> variants = repository.variants(PROCESS, null, null, 2);

    // then the list truncates by rank while shares stay against ALL ended-with-variant instances
    assertThat(variants).extracting(VariantRow::variantHash).containsExactly("1", "2");
    assertThat(variants.get(0).share()).isEqualTo(0.5); // 3 of 6, not 3 of 5
  }

  @Test
  void shouldServeNoVariantsForAnUnknownProcess() {
    // given / when / then — nothing folded for the process
    assertThat(repository.variants("unknown", null, null, 10)).isEmpty();
  }

  private void seedCatalogRow(final long variantHash, final String elements) {
    ServingTestSupport.seedRow(
        fixture,
        "variant-catalog",
        String.valueOf(variantHash),
        List.of(variantHash, PROCESS, elements));
  }

  private static List<Fact> ended(final long... durationsMs) {
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
