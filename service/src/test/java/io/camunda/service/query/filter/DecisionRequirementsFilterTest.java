/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementsFilterTest {

  private DecisionRequirementsServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionRequirementsSearchQueryStub().registerWith(client);
    services = new DecisionRequirementsServices(null, client);
  }

  @Test
  public void shouldQueryByDecisionRequirementsKey() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.decisionRequirementsKeys(124L));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
        FilterBuilders.decisionRequirements(f -> f.dmnDecisionRequirementsIds("dId"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirements(f -> f.dmnDecisionRequirementsNames("n"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
        FilterBuilders.decisionRequirements(f -> f.dmnDecisionRequirementsNames("n").versions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.filter(decisionRequirementFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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

  @Test
  public void shouldReturnDecisionRequirements() {
    // given
    final DecisionRequirementsQuery searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery().build();

    // when
    final SearchQueryResult<DecisionRequirementsEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final DecisionRequirementsEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(124L);
  }
}
