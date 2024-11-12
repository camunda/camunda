/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementsQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByDecisionRequirementsKey() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.decisionRequirementsKeys(124L));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

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
  public void shouldQueryByDecisionRequirementsVersion() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirements(f -> f.versions(1));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsTenantId() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.tenantIds("t"));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

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
  public void shouldQueryByDecisionRequirementsDecisionRequirementsId() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.decisionRequirementsIds("dId"));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsId");
              assertThat(t.value().stringValue()).isEqualTo("dId");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsName() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirements(f -> f.names("n"));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsNameAndVersion() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.names("n").versions(1));

    // when
    final var searchRequest = transformQuery(decisionRequirementFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }
}
