/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

public final class FlowNodeInstanceFilterTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByFlowNodeInstanceKey() {
    final var filter = FilterBuilders.flowNodeInstance(f -> f.flowNodeInstanceKeys(1L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    final var filter =
        FilterBuilders.flowNodeInstance(f -> f.processDefinitionIds("complexProcess"));
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    final var filter =
        FilterBuilders.flowNodeInstance(f -> f.states(FlowNodeState.COMPLETED.name()));
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }
}
