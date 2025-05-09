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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchOperationItemFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByBatchOperationId() {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.batchOperationIds("123"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("batchOperationId");
              assertThat(t.value().stringValue()).isEqualTo("123");
            });
  }

  @Test
  void shouldQueryLegacyByBatchOperationId() {
    // given
    final var batchIdUuid = UUID.randomUUID().toString();
    final var filter = FilterBuilders.batchOperationItem(f -> f.batchOperationIds(batchIdUuid));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("batchOperationId");
              assertThat(t.value().stringValue()).isEqualTo(batchIdUuid);
            });
  }

  @Test
  void shouldQueryItemKey() {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.itemKeys(123L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("itemKey");
              assertThat(t.value().longValue()).isEqualTo(123L);
            });
  }

  @Test
  void shouldQueryByProcessInstanceKey() {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.processInstanceKeys(456L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(456L);
            });
  }

  @Test
  void shouldQueryByState() {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.state("ACTIVE"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("ACTIVE");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f ->
                f.batchOperationIds("123")
                    .state("ACTIVE")
                    .itemKeys(123L)
                    .processInstanceKeys(456L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(4);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("batchOperationId");
              assertThat(t.value().stringValue()).isEqualTo("123");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("ACTIVE");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("itemKey");
              assertThat(t.value().longValue()).isEqualTo(123L);
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(3).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(456L);
            });
  }
}
