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
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
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
    final var filter = FilterBuilders.globalListener(f -> f.types("START"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("START");
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
    final var filter = FilterBuilders.globalListener(f -> f.eventTypes("created"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("eventTypes");
              assertThat(t.value().stringValue()).isEqualTo("created");
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
    final var filter = FilterBuilders.globalListener(f -> f.sources("PROCESS_DEFINITION"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("source");
              assertThat(t.value().stringValue()).isEqualTo("PROCESS_DEFINITION");
            });
  }

  @Test
  void shouldQueryByListenerType() {
    // given
    final var filter = FilterBuilders.globalListener(f -> f.listenerTypes("EXECUTION_LISTENER"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerType");
              assertThat(t.value().stringValue()).isEqualTo("EXECUTION_LISTENER");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.globalListener(
            f ->
                f.listenerIds("listener-123")
                    .types("START")
                    .retries(3)
                    .eventTypes("created")
                    .afterNonGlobal(true)
                    .priorities(10)
                    .sources("PROCESS_DEFINITION")
                    .listenerTypes("EXECUTION_LISTENER"));

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
              assertThat(t.value().stringValue()).isEqualTo("START");
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
              assertThat(t.value().stringValue()).isEqualTo("PROCESS_DEFINITION");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(7).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerType");
              assertThat(t.value().stringValue()).isEqualTo("EXECUTION_LISTENER");
            });
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenEnabled() {
    // given
    final var authorization =
        Authorization.of(a -> a.processDefinition().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.globalListener(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.globalListener(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldIgnoreTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.globalListener(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }
}
