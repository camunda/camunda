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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchOperationFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByBatchOperationId() {
    // given
    final var filter = FilterBuilders.batchOperation(f -> f.batchOperationKeys("123"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("id");
              assertThat(t.value().stringValue()).isEqualTo("123");
            });
  }

  @Test
  void shouldQueryLegacyByBatchOperationId() {
    // given
    final var batchIdUuid = UUID.randomUUID().toString();
    final var filter = FilterBuilders.batchOperation(f -> f.batchOperationKeys(batchIdUuid));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("id");
              assertThat(t.value().stringValue()).isEqualTo(batchIdUuid);
            });
  }

  @Test
  void shouldQueryByState() {
    // given
    final var filter = FilterBuilders.batchOperation(f -> f.states("ACTIVE"));

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
  void shouldQueryByOperationType() {
    // given
    final var filter = FilterBuilders.batchOperation(f -> f.operationTypes("CREATE"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("CREATE");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.batchOperation(
            f -> f.batchOperationKeys("123").states("ACTIVE").operationTypes("CREATE"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("id");
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
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("CREATE");
            });
  }

  @Test
  public void shouldIgnoreAuthorizationCheckEventWhenEnabled() {
    // given
    final var authorization =
        Authorization.of(a -> a.batchOperation().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.batchOperation(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.batchOperation(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.batchOperation(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }
}
