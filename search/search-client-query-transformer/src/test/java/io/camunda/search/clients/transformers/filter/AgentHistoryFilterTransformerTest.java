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
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AgentHistoryFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByAgentInstanceKey() {
    // given
    final var filter = FilterBuilders.agentInstanceHistory(f -> f.agentInstanceKeys(50L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("agentInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(50L);
            });
  }

  @Test
  void shouldQueryByHistoryItemKey() {
    // given
    final var filter = FilterBuilders.agentInstanceHistory(f -> f.historyItemKeys(100L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(100L);
            });
  }

  @Test
  void shouldQueryByRole() {
    // given
    final var filter =
        FilterBuilders.agentInstanceHistory(f -> f.roles(AgentInstanceHistoryRole.ASSISTANT));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("role");
              assertThat(t.value().stringValue()).isEqualTo("ASSISTANT");
            });
  }

  @Test
  void shouldQueryByElementInstanceKey() {
    // given
    final var filter = FilterBuilders.agentInstanceHistory(f -> f.elementInstanceKeys(200L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("elementInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(200L);
            });
  }

  @Test
  void shouldQueryByJobKey() {
    // given
    final var filter = FilterBuilders.agentInstanceHistory(f -> f.jobKeys(300L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("jobKey");
              assertThat(t.value().longValue()).isEqualTo(300L);
            });
  }

  @Test
  void shouldQueryByIteration() {
    // given
    final var filter = FilterBuilders.agentInstanceHistory(f -> f.iterations(3));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("iteration");
              assertThat(t.value().intValue()).isEqualTo(3);
            });
  }

  @Test
  void shouldQueryByCommitStatus() {
    // given
    final var filter =
        FilterBuilders.agentInstanceHistory(
            f -> f.commitStatuses(AgentInstanceHistoryCommitStatus.COMMITTED));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("commitStatus");
              assertThat(t.value().stringValue()).isEqualTo("COMMITTED");
            });
  }

  @Test
  void shouldQueryByProducedAt() {
    // given
    final var date = OffsetDateTime.parse("2024-06-01T00:00:00Z");
    final var filter =
        FilterBuilders.agentInstanceHistory(f -> f.producedAtOperations(Operation.eq(date)));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("producedAt");
              assertThat(t.value().stringValue()).contains("2024-06-01");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.agentInstanceHistory(
            f ->
                f.agentInstanceKeys(50L)
                    .historyItemKeys(100L)
                    .roles(AgentInstanceHistoryRole.USER)
                    .elementInstanceKeys(200L)
                    .jobKeys(300L)
                    .iterations(2)
                    .commitStatuses(AgentInstanceHistoryCommitStatus.PENDING)
                    .producedAtOperations(
                        Operation.eq(OffsetDateTime.parse("2024-06-01T00:00:00Z"))));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest.queryOption()).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) searchRequest.queryOption()).must()).hasSize(8);
  }
}
