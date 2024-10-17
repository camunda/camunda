/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.filter.FilterBuilders.dateValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DecisionInstanceQueryTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByKey() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionInstanceKeys(124L));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(124L);
            });
  }

  @Test
  void shouldQueryByDecisionVersion() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionVersions(1));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionVersion");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  void shouldQueryByTenantId() {
    // given
    final var decisionInstanceFilter = FilterBuilders.decisionInstance(f -> f.tenantIds("t"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("t");
            });
  }

  @Test
  void shouldQueryByDmnDecisionId() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionIds("dId"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionId");
              assertThat(t.value().stringValue()).isEqualTo("dId");
            });
  }

  @Test
  void shouldQueryByDecisionKey() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionKeys(12345L));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionDefinitionId");
              assertThat(t.value().stringValue()).isEqualTo("12345");
            });
  }

  @Test
  void shouldQueryByDmnDecisionName() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionNames("n"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionName");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });
  }

  @Test
  void shouldQueryByDmnDecisionNameAndDecisionVersion() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(
            f -> f.decisionDefinitionNames("n").decisionDefinitionVersions(1));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("decisionName");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("decisionVersion");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  void shouldQueryByEvaluationDate() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(
            f ->
                f.evaluationDate(
                    dateValue(
                        d ->
                            d.after(
                                    OffsetDateTime.of(
                                        LocalDateTime.of(2024, 1, 2, 3, 4, 5), ZoneOffset.UTC))
                                .before(
                                    OffsetDateTime.of(
                                        LocalDateTime.of(2024, 2, 3, 4, 5, 6), ZoneOffset.UTC)))));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("evaluationDate");
              assertThat(t.gte()).isEqualTo("2024-01-02T03:04:05.000+0000");
              assertThat(t.lt()).isEqualTo("2024-02-03T04:05:06.000+0000");
            });
  }
}
