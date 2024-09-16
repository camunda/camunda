/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static io.camunda.service.query.filter.DecisionInstanceSearchQueryStub.KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceFilterTest {

  private DecisionInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionInstanceSearchQueryStub().registerWith(client);
    services = new DecisionInstanceServices(null, client);
  }

  @Test
  void shouldQueryByDecisionKey() {
    // given
    final var decisionInstanceFilter = FilterBuilders.decisionInstance(f -> f.keys(124L));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

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
  void shouldQueryByDecisionVersion() {
    // given
    final var decisionInstanceFilter = FilterBuilders.decisionInstance(f -> f.decisionVersions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

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
  void shouldQueryByDmnDecisionId() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.dmnDecisionIds("dId"));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionId");
              assertThat(t.value().stringValue()).isEqualTo("dId");
            });
  }

  @Test
  void shouldQueryByDmnDecisionName() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.dmnDecisionNames("n"));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
        FilterBuilders.decisionInstance(f -> f.dmnDecisionNames("n").decisionVersions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

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
  void shouldReturnDecisionInstances() {
    // given
    final DecisionInstanceQuery searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery().build();

    // when
    final SearchQueryResult<DecisionInstanceEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final DecisionInstanceEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(KEY);
  }
}
