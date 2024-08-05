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
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionDefinitionFilterTest {

  private DecisionDefinitionServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionDefinitionSearchQueryStub().registerWith(client);
    services = new DecisionDefinitionServices(null, client);
  }

  @Test
  public void shouldReturnDecisionDefinition() {
    // given
    final DecisionDefinitionQuery searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery().build();

    // when
    final SearchQueryResult<DecisionDefinitionEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final DecisionDefinitionEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }

  @Test
  public void shouldQueryByDecisionDefinitionKey() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.keys(123L));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
  public void shouldQueryByDecisionDefinitionId() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.ids("1234"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("1234");
            });
  }

  @Test
  public void shouldQueryByDecisionDefinitionName() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.names("foo"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByDecisionDefinitionVersion() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.versions(2));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().intValue()).isEqualTo(2);
            });
  }

  @Test
  public void shouldQueryByDecisionId() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionIds("foo"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsId() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsIds("567"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("567");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsKey() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsKeys(5678L));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsKey");
              assertThat(t.value().longValue()).isEqualTo(5678L);
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsName() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsNames("foo"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsName");
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsVersion() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsVersions(1));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsVersion");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.tenantIds("foo"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByTenantIdAndDecisionDefinitionName() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.tenantIds("tenant").names("foo"));
    final var searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.filter(decisionDefinitionFilter));

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
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("tenant");
            });
  }
}
