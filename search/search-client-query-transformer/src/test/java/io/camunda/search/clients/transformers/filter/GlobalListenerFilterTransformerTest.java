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
import org.junit.jupiter.api.Test;

class GlobalListenerFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByListenerId() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.listenerIds("listener-123"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerId");
              assertThat(t.value().stringValue()).isEqualTo("listener-123");
            });
  }

  @Test
  void shouldQueryByType() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.types("my-job"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("my-job");
            });
  }

  @Test
  void shouldQueryByRetries() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.retries(3));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("retries");
              assertThat(t.value().intValue()).isEqualTo(3);
            });
  }

  @Test
  void shouldQueryByEventTypes() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.eventTypes("creating"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("eventTypes");
              assertThat(t.value().stringValue()).isEqualTo("creating");
            });
  }

  @Test
  void shouldQueryByAfterNonGlobal() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.afterNonGlobal(true));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("afterNonGlobal");
              assertThat(t.value().booleanValue()).isTrue();
            });
  }

  @Test
  void shouldQueryByPriority() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.priorities(10));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("priority");
              assertThat(t.value().intValue()).isEqualTo(10);
            });
  }

  @Test
  void shouldQueryBySource() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.sources("CONFIGURATION"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("source");
              assertThat(t.value().stringValue()).isEqualTo("CONFIGURATION");
            });
  }

  @Test
  void shouldQueryByListenerType() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.listenerTypes("USER_TASK"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerType");
              assertThat(t.value().stringValue()).isEqualTo("USER_TASK");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.globalListener(
            f ->
                f.listenerIds("listener-123")
                    .types("my-job")
                    .retries(3)
                    .eventTypes("created")
                    .afterNonGlobal(true)
                    .priorities(10)
                    .sources("API")
                    .listenerTypes("USER_TASK"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(8);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerId");
              assertThat(t.value().stringValue()).isEqualTo("listener-123");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("my-job");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("retries");
              assertThat(t.value().intValue()).isEqualTo(3);
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(3).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("eventTypes");
              assertThat(t.value().stringValue()).isEqualTo("created");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(4).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("afterNonGlobal");
              assertThat(t.value().booleanValue()).isTrue();
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(5).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("priority");
              assertThat(t.value().intValue()).isEqualTo(10);
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(6).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("source");
              assertThat(t.value().stringValue()).isEqualTo("API");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(7).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerType");
              assertThat(t.value().stringValue()).isEqualTo("USER_TASK");
            });
  }
}
