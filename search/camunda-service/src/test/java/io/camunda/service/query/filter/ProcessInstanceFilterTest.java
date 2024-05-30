/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.query.DataStoreBoolQuery;
import io.camunda.data.clients.query.DataStoreHasChildQuery;
import io.camunda.data.clients.query.DataStoreQueryVariant;
import io.camunda.data.clients.query.DataStoreTermQuery;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.query.filter.ProcessInstanceFilter.Builder;
import io.camunda.service.query.search.ProcessInstanceQuery;
import io.camunda.service.query.search.SearchQueryBuilders;
import io.camunda.service.query.search.SearchQueryResult;
import io.camunda.service.util.StubbedDataStoreClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceFilterTest {

  private ProcessInstanceServices services;
  private StubbedDataStoreClient client;

  @BeforeEach
  public void before() {
    client = new StubbedDataStoreClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(client);
  }

  @Test
  public void shouldQueryOnlyByProcessInstances() {
    // configure services

    // a) create stubbed data store client
    final StubbedDataStoreClient client = new StubbedDataStoreClient();

    // b) register request handler (stub) that returns a response
    // based on what needs to be tested
    final ProcessInstanceSearchQueryStub queryStub = new ProcessInstanceSearchQueryStub();
    queryStub.registerWith(client);

    // c) create services by using the stubbed client
    final ProcessInstanceServices services = new ProcessInstanceServices(client);

    // given
    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then

    // Assert: Transformation from ProcessInstanceQuery to DataStoreSearchRequest

    // a) verify search request
    // The stubbed client collects all received search requests
    // that can be used for assertions
    final DataStoreSearchRequest searchRequest = client.getSingleSearchRequest();

    // b) verify that the search request has been constructed properly
    // depending on the actual search query
    final DataStoreQueryVariant queryVariant = searchRequest.query().queryVariant();
    assertThat((queryVariant))
        .isInstanceOfSatisfying(
            DataStoreTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });
  }

  @Test
  public void shouldReturnProcessInstance() {
    // configure services

    // a) create stubbed data store client
    final StubbedDataStoreClient client = new StubbedDataStoreClient();

    // b) register request handler (stub) that returns a response
    // based on what needs to be tested
    final ProcessInstanceSearchQueryStub queryStub = new ProcessInstanceSearchQueryStub();
    queryStub.registerWith(client);

    // c) create services by using the stubbed client
    final ProcessInstanceServices services = new ProcessInstanceServices(client);

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
    assertThat(item.getKey()).isEqualTo(1234L);
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

    final var queryVariant = searchRequest.query().queryVariant();
    assertThat(queryVariant).isInstanceOf(DataStoreBoolQuery.class);
    assertThat(((DataStoreBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((DataStoreBoolQuery) queryVariant).must().get(0).queryVariant())
        .isInstanceOfSatisfying(
            DataStoreTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((DataStoreBoolQuery) queryVariant).must().get(1).queryVariant())
        .isInstanceOfSatisfying(
            DataStoreTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(4503599627370497L);
            });
  }

  @Test
  public void shouldQueryByVariableValues() {
    // given
    final var variableFilter = FilterBuilders.variable((v) -> v.name("foo").gt(123));
    final var filter = FilterBuilders.processInstance((f) -> f.variable(variableFilter));
    final var searchQuery = SearchQueryBuilders.processInstanceSearchQuery((b) -> b.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryVariant();
    assertThat(queryVariant).isInstanceOf(DataStoreBoolQuery.class);
    assertThat(((DataStoreBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((DataStoreBoolQuery) queryVariant).must().get(0).queryVariant())
        .isInstanceOfSatisfying(
            DataStoreTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("joinRelation");
              assertThat(t.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((DataStoreBoolQuery) queryVariant).must().get(1).queryVariant())
        .isInstanceOf(DataStoreHasChildQuery.class);
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = new Builder().build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).isNull();
    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_");

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
            .variable(new VariableValueFilter.Builder().build())
            .build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).hasSize(1).contains(1L);
    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_alias");

    assertThat(processInstanceFilter.active()).isTrue();
    assertThat(processInstanceFilter.canceled()).isTrue();
    assertThat(processInstanceFilter.completed()).isTrue();
    assertThat(processInstanceFilter.finished()).isTrue();
    assertThat(processInstanceFilter.running()).isTrue();
    assertThat(processInstanceFilter.retriesLeft()).isTrue();
  }
}
