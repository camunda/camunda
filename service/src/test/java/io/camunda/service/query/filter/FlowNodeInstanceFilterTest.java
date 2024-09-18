/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.service.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class FlowNodeInstanceFilterTest {

  private FlowNodeInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new FlowNodeInstanceSearchQueryStub().registerWith(client);
    services = new FlowNodeInstanceServices(null, client, null, null);
  }

  @Test
  public void shouldReturnFlowNodeInstance() {
    // given
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final FlowNodeInstanceEntity item = searchQueryResult.items().getFirst();
    assertThat(item.key()).isEqualTo(1L);
  }

  @Test
  public void shouldQueryByFlowNodeInstanceKey() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.flowNodeInstanceKeys(1L));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
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
              assertThat(t.value().longValue()).isEqualTo(1L);
            });
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.processInstanceKeys(2L));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(2L);
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.processDefinitionKeys(3L));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(3L);
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.bpmnProcessIds("complexProcess"));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("complexProcess");
            });
  }

  @Test
  public void shouldQueryByState() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.states(FlowNodeState.COMPLETED));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("COMPLETED");
            });
  }

  @Test
  public void shouldQueryByType() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.types(FlowNodeType.SERVICE_TASK));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("SERVICE_TASK");
            });
  }

  @Test
  public void shouldQueryByFlowNodeId() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.flowNodeIds("startEvent_1"));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeId");
              assertThat(t.value().stringValue()).isEqualTo("startEvent_1");
            });
  }

  @Test
  public void shouldQueryByTreePath() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.treePaths("12345/6789"));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("treePath");
              assertThat(t.value().stringValue()).isEqualTo("12345/6789");
            });
  }

  @Test
  public void shouldQueryByIncidentKey() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.incidentKeys(5L));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("incidentKey");
              assertThat(t.value().longValue()).isEqualTo(5L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.tenantIds("<default>"));
    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.filter(filter));
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
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }
}
