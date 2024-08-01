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
import io.camunda.service.DecisionRequirementServices;
import io.camunda.service.entities.DecisionRequirementEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.DecisionRequirementQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementFilterTest {

  private DecisionRequirementServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionRequirementSearchQueryStub().registerWith(client);
    services = new DecisionRequirementServices(null, client);
  }

  @Test
  public void shouldQueryByDecisionRequirementKey() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirement(f -> f.keys(123L));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
              assertThat(t.value().longValue()).isEqualTo(123L);
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementVersion() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirement(f -> f.versions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
  public void shouldQueryByDecisionRequirementId() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirement(f -> f.ids("id"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
              assertThat(t.field()).isEqualTo("id");
              assertThat(t.value().stringValue()).isEqualTo("id");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementTenantId() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirement(f -> f.tenantIds("t"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
  public void shouldQueryByDecisionRequirementDecisionRequirementsId() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirement(f -> f.decisionRequirementsIds("dId"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
  public void shouldQueryByDecisionRequirementName() {
    // given
    final var decisionRequirementFilter = FilterBuilders.decisionRequirement(f -> f.names("n"));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
  public void shouldQueryByDecisionRequirementNameAndVersion() {
    // given
    final var decisionRequirementFilter =
        FilterBuilders.decisionRequirement(f -> f.names("n").versions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery(
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
  public void shouldReturnDecisionRequirement() {
    // given
    final DecisionRequirementQuery searchQuery =
        SearchQueryBuilders.decisionRequirementSearchQuery().build();

    // when
    final SearchQueryResult<DecisionRequirementEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final DecisionRequirementEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }
}
