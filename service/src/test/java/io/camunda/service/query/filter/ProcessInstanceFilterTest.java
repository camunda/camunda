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
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceFilterTest {

  private ProcessInstanceServices services;
  private StubbedCamundaSearchClient client;
  private StubbedBrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(brokerClient, client);
  }

  @Test
  public void shouldQueryOnlyByProcessInstances() {
    // given
    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    services.search(searchQuery);

    // then

    // Assert: Transformation from ProcessInstanceQuery to DataStoreSearchRequest

    // a) verify search request
    // The stubbed client collects all received search requests
    // that can be used for assertions
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    // b) verify that the search request has been constructed properly
    // depending on the actual search query
    final SearchQueryOption queryVariant = searchRequest.query().queryOption();
    assertThat((queryVariant))
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then

    // Assert: Transformation from DataStoreSearchResponse to
    // SearchQueryResult<ProcessInstanceEntity>

    // a) verify search query result
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);

    // b) assert items
    final ProcessInstanceEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance((f) -> f.processInstanceKeys(4503599627370497L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery((q) -> q.filter(processInstanceFilter));

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
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(4503599627370497L);
            });
  }

  @Test
  public void shouldQueryByVariableValues() {
    // given
    final var variableFilter = FilterBuilders.variableValue((v) -> v.name("foo").gt(123));
    final var filter =
        FilterBuilders.processInstance(
            (f) -> f.variable(variableFilter).variable((v) -> v.name("bar").neq(789L)));
    final var searchQuery = SearchQueryBuilders.processInstanceSearchQuery((b) -> b.filter(filter));

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
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOf(SearchBoolQuery.class);
  }

  @Test
  public void shouldQueryByStartAndEndDate() {
    // given
    final var startDateFilter =
        FilterBuilders.dateValue((d) -> d.after(OffsetDateTime.now()).before(OffsetDateTime.now()));
    final var endDateFilter =
        FilterBuilders.dateValue((d) -> d.after(OffsetDateTime.now()).before(OffsetDateTime.now()));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(
            (b) -> b.filter((f) -> f.startDate(startDateFilter).endDate(endDateFilter)));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = new Builder().build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).isEmpty();
    //    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_");

    assertThat(processInstanceFilter.active()).isFalse();
    assertThat(processInstanceFilter.canceled()).isFalse();
    assertThat(processInstanceFilter.completed()).isFalse();
    assertThat(processInstanceFilter.finished()).isFalse();
    assertThat(processInstanceFilter.running()).isFalse();
    assertThat(processInstanceFilter.retriesLeft()).isFalse();
  }

  @Test
  public void shouldSetFilterValues() {
    // given
    final var processInstanceFilterBuilder = new Builder();

    // when
    final var processInstanceFilter =
        processInstanceFilterBuilder
            .active()
            .canceled()
            .completed()
            .finished()
            .retriesLeft()
            .running()
            .processInstanceKeys(List.of(1L))
            .variable(new VariableValueFilter.Builder().name("foo").build())
            .build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).hasSize(1).contains(1L);
    //    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_alias");

    assertThat(processInstanceFilter.active()).isTrue();
    assertThat(processInstanceFilter.canceled()).isTrue();
    assertThat(processInstanceFilter.completed()).isTrue();
    assertThat(processInstanceFilter.finished()).isTrue();
    assertThat(processInstanceFilter.running()).isTrue();
    assertThat(processInstanceFilter.retriesLeft()).isTrue();
  }
}
