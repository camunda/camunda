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
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AgentInstanceFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByAgentInstanceKey() {
    final var filter = FilterBuilders.agentInstance(f -> f.agentInstanceKeys(100L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(100L);
            });
  }

  @Test
  void shouldQueryByElementInstanceKey() {
    final var filter = FilterBuilders.agentInstance(f -> f.elementInstanceKeys(1L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("elementInstanceKeys");
              assertThat(t.value().longValue()).isEqualTo(1L);
            });
  }

  @Test
  void shouldQueryByProcessInstanceKey() {
    final var filter = FilterBuilders.agentInstance(f -> f.processInstanceKeys(200L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(200L);
            });
  }

  @Test
  void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.agentInstance(f -> f.processDefinitionKeys(400L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(400L);
            });
  }

  @Test
  void shouldQueryByProcessDefinitionId() {
    final var filter = FilterBuilders.agentInstance(f -> f.processDefinitionIds("myProcess"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("myProcess");
            });
  }

  @Test
  void shouldQueryByProcessDefinitionVersion() {
    final var filter = FilterBuilders.agentInstance(f -> f.processDefinitionVersions(2));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionVersion");
              assertThat(t.value().intValue()).isEqualTo(2);
            });
  }

  @Test
  void shouldQueryByVersionTag() {
    final var filter = FilterBuilders.agentInstance(f -> f.versionTags("v1"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("versionTag");
              assertThat(t.value().stringValue()).isEqualTo("v1");
            });
  }

  @Test
  void shouldQueryByElementId() {
    final var filter = FilterBuilders.agentInstance(f -> f.elementIds("Task_1"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("elementId");
              assertThat(t.value().stringValue()).isEqualTo("Task_1");
            });
  }

  @Test
  void shouldQueryByStatus() {
    final var filter = FilterBuilders.agentInstance(f -> f.statuses("RUNNING"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("status");
              assertThat(t.value().stringValue()).isEqualTo("RUNNING");
            });
  }

  @Test
  void shouldQueryByTenantId() {
    final var filter = FilterBuilders.agentInstance(f -> f.tenantIds("<default>"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }

  @Test
  void shouldQueryByCreationDate() {
    final var date = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    final var filter =
        FilterBuilders.agentInstance(f -> f.creationDateOperations(Operation.eq(date)));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("creationDate");
              assertThat(t.value().stringValue()).contains("2024-01-01");
            });
  }

  @Test
  void shouldQueryByLastUpdatedDate() {
    final var date = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    final var filter =
        FilterBuilders.agentInstance(f -> f.lastUpdatedDateOperations(Operation.eq(date)));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("lastUpdatedDate");
              assertThat(t.value().stringValue()).contains("2024-01-02");
            });
  }

  @Test
  void shouldQueryByCompletionDate() {
    final var date = OffsetDateTime.parse("2024-01-03T00:00:00Z");
    final var filter =
        FilterBuilders.agentInstance(f -> f.completionDateOperations(Operation.eq(date)));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("completionDate");
              assertThat(t.value().stringValue()).contains("2024-01-03");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    final var filter =
        FilterBuilders.agentInstance(
            f ->
                f.agentInstanceKeys(100L)
                    .elementInstanceKeys(1L)
                    .processInstanceKeys(200L)
                    .processDefinitionKeys(400L)
                    .processDefinitionIds("myProcess")
                    .processDefinitionVersions(2)
                    .versionTags("v1")
                    .elementIds("Task_1")
                    .statuses("RUNNING")
                    .tenantIds("<default>")
                    .creationDateOperations(
                        Operation.eq(OffsetDateTime.parse("2024-01-01T00:00:00Z")))
                    .lastUpdatedDateOperations(
                        Operation.eq(OffsetDateTime.parse("2024-01-02T00:00:00Z")))
                    .completionDateOperations(
                        Operation.eq(OffsetDateTime.parse("2024-01-03T00:00:00Z"))));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption()).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) searchRequest.queryOption()).must()).hasSize(13);
  }
}
