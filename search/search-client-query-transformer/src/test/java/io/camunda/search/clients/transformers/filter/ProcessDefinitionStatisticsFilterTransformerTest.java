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
import io.camunda.search.clients.query.SearchHasParentQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ProcessDefinitionStatisticsFilterTransformerTest
    extends AbstractTransformerTest {

  public static final long PROCESS_DEFINITION_KEY = 123L;

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(PROCESS_DEFINITION_KEY, f -> f);

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 2);
  }

  @Test
  public void shouldQueryByParentProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY, f -> f.parentProcessInstanceKeys(567L));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    assertIsSearchHasParentTermQuery(
        searchBoolQuery.must().get(2).queryOption(), "parentProcessInstanceKey", 567L);
  }

  @Test
  public void shouldQueryByParentFlowNodeInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY, f -> f.parentFlowNodeInstanceKeys(567L));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    assertIsSearchHasParentTermQuery(
        searchBoolQuery.must().get(2).queryOption(), "parentFlowNodeInstanceKey", 567L);
  }

  @Test
  public void shouldQueryByStartDateAndEndDate() {
    // given
    final var dateAfter = OffsetDateTime.of(2024, 3, 12, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateBefore = OffsetDateTime.of(2024, 7, 15, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateFilter = List.of(Operation.gte(dateAfter), Operation.lt(dateBefore));
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY,
            f -> f.startDateOperations(dateFilter).endDateOperations(dateFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    final var hasParentQuery =
        assertIsSearchHasParentQuery(searchBoolQuery.must().get(2).queryOption());
    final var parentBoolQuery = assertIsSearchBoolQuery(hasParentQuery, 2);
    assertThat(parentBoolQuery.must()).hasSize(2);
    assertThat(parentBoolQuery.must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("startDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });

    assertThat(parentBoolQuery.must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("endDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });
  }

  @Test
  public void shouldQueryByState() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY, f -> f.states("ACTIVE"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    final var hasParentQuery =
        assertIsSearchHasParentQuery(searchBoolQuery.must().get(2).queryOption());
    assertIsSearchTermQuery(hasParentQuery, "state", "ACTIVE");
  }

  @Test
  public void shouldQueryByIncident() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY, f -> f.hasIncident(true));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    final var hasParentQuery =
        assertIsSearchHasParentQuery(searchBoolQuery.must().get(2).queryOption());
    assertThat(hasParentQuery)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("incident");
              assertThat(searchTermQuery.value().booleanValue()).isEqualTo(true);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processDefinitionStatisticsFilter(
            PROCESS_DEFINITION_KEY, f -> f.tenantIds("tenant"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    final var searchBoolQuery = assertIsSearchBoolQueryWithDefaultFilter(queryVariant, 3);
    final var hasParentQuery =
        assertIsSearchHasParentQuery(searchBoolQuery.must().get(2).queryOption());
    assertIsSearchTermQuery(hasParentQuery, "tenantId", "tenant");
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = (new ProcessDefinitionStatisticsFilter.Builder(1L)).build();

    // then
    assertThat(processInstanceFilter.processDefinitionKey()).isEqualTo(1L);
    assertThat(processInstanceFilter.processInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.parentFlowNodeInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.startDateOperations()).isEmpty();
    assertThat(processInstanceFilter.endDateOperations()).isEmpty();
    assertThat(processInstanceFilter.stateOperations()).isEmpty();
    assertThat(processInstanceFilter.hasIncident()).isNull();
    assertThat(processInstanceFilter.tenantIdOperations()).isEmpty();
  }

  private SearchBoolQuery assertIsSearchBoolQuery(
      final SearchQueryOption queryVariant, final int size) {
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.must()).hasSize(size);
              assertThat(searchBoolQuery.filter()).isEmpty();
              assertThat(searchBoolQuery.mustNot()).isEmpty();
              assertThat(searchBoolQuery.should()).isEmpty();
            });
    return (SearchBoolQuery) queryVariant;
  }

  private SearchBoolQuery assertIsSearchBoolQueryWithDefaultFilter(
      final SearchQueryOption queryVariant, final int size) {
    final var searchBoolQuery = assertIsSearchBoolQuery(queryVariant, size);
    assertIsSearchTermQuery(
        searchBoolQuery.must().get(0).queryOption(), "joinRelation", "activity");
    assertIsSearchHasParentTermQuery(
        searchBoolQuery.must().get(1).queryOption(),
        "processDefinitionKey",
        PROCESS_DEFINITION_KEY);
    return searchBoolQuery;
  }

  private void assertIsSearchTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final String expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().stringValue()).isEqualTo(expectedValue);
            });
  }

  private SearchQueryOption assertIsSearchHasParentQuery(
      final SearchQueryOption searchQueryOption) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchHasParentQuery.class,
            (searchHasParentQuery) ->
                assertThat(searchHasParentQuery.parentType()).isEqualTo("processInstance"));
    return ((SearchHasParentQuery) searchQueryOption).query().queryOption();
  }

  private void assertIsSearchHasParentTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final Long expectedValue) {
    final var hasParentQuery = assertIsSearchHasParentQuery(searchQueryOption);
    assertThat(hasParentQuery)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().longValue()).isEqualTo(expectedValue);
            });
  }
}
