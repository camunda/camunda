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
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

class GlobalListenerFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByListenerId() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.listenerIds("listener-1"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerId");
              assertThat(t.value().stringValue()).isEqualTo("listener-1");
            });
  }

  @Test
  void shouldQueryByType() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.types("io.camunda.MyListener"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("io.camunda.MyListener");
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
}
