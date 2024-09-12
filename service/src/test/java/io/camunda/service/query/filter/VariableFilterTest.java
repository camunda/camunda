/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.VariableServices;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.sort.SortOrder;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class VariableFilterTest {

  private VariableServices services;
  private StubbedCamundaSearchClient client;
  private StubbedBrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new VariableSearchQueryStub().registerWith(client);
    services = new VariableServices(brokerClient, null, null);
  }

  @Test
  public void shouldQueryOnlyByVariables() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (v) -> v.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    services.search(searchQuery);

    // then

    // Assert: Transformation from VariableQuery to DataStoreSearchRequest

    // a) verify search request
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    // b) verify that the search request has been constructed properly
    // depending on the actual search query
    final SearchQueryOption queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("name");
                        assertThat(term.value().stringValue()).isEqualTo("foo");
                      });
            });
  }

  @Test
  public void shouldReturnVariables() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (v) -> v.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    final SearchQueryResult<VariableEntity> searchQueryResult = services.search(searchQuery);

    // then

    // Assert: Transformation from DataStoreSearchResponse to
    // SearchQueryResult<VariableEntity>

    // a) verify search query result
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);

    // b) assert items
    final VariableEntity item = searchQueryResult.items().get(0);
    assertThat(item.name()).isEqualTo("bar");
  }

  @Test
  public void shouldQueryByVariableScopeKey() {
    // given
    final var variableFilter = FilterBuilders.variable((v) -> v.scopeKeys(4503599627370497L));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("scopeKey");
              assertThat(t.value().longValue()).isEqualTo(4503599627370497L);
            });
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var variableFilter = new VariableFilter.Builder().build();

    // then
    assertThat(variableFilter.variableFilters()).isEmpty();
    assertThat(variableFilter.scopeKeys()).isEmpty();
    assertThat(variableFilter.processInstanceKeys()).isEmpty();
    assertThat(variableFilter.orConditions()).isFalse();
    assertThat(variableFilter.onlyRuntimeVariables()).isFalse();
  }

  @Test
  public void shouldSetFilterValuesWithGtAndGte() {
    // given
    var variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterGt =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").gt("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterGt.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterGt.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterGt.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterGt.variableFilters()).hasSize(1).extracting("gt").contains("1000");
    assertThat(variableFilterGt.orConditions()).isTrue();
    assertThat(variableFilterGt.onlyRuntimeVariables()).isTrue();

    variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterGte =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").gte("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterGte.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterGte.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterGte.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterGte.variableFilters()).hasSize(1).extracting("gte").contains("1000");
    assertThat(variableFilterGte.orConditions()).isTrue();
    assertThat(variableFilterGte.onlyRuntimeVariables()).isTrue();
  }

  @Test
  public void shouldSetFilterValuesWithLtAndLte() {
    // given
    var variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterLt =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").lt("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterLt.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterLt.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterLt.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterLt.variableFilters()).hasSize(1).extracting("lt").contains("1000");
    assertThat(variableFilterLt.orConditions()).isTrue();
    assertThat(variableFilterLt.onlyRuntimeVariables()).isTrue();

    variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterLte =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").lte("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterLte.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterLte.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterLte.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterLte.variableFilters()).hasSize(1).extracting("lte").contains("1000");
    assertThat(variableFilterLte.orConditions()).isTrue();
    assertThat(variableFilterLte.onlyRuntimeVariables()).isTrue();
  }

  @Test
  public void shouldApplySortConditionByValueASC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.value().asc()));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    // Assert the sort condition
    final var sort = searchRequest.sort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(2);

    final boolean sortByValueConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("value") && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByValueConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByValueDESC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.value().desc()));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    // Assert the sort condition
    final var sort = searchRequest.sort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(2);

    final boolean sortByValueConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("value") && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByValueConditionCheck).isTrue();
  }
}
