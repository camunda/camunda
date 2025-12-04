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
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BatchOperationItemFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByBatchOperationKey() {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.batchOperationKeys("123"));

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
  void shouldQueryLegacyByBatchOperationKey() {
    // given
    final var batchIdUuid = UUID.randomUUID().toString();
    final var filter = FilterBuilders.batchOperationItem(f -> f.batchOperationKeys(batchIdUuid));

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

  @ParameterizedTest
  @CsvSource(value = {"ACTIVE, SCHEDULED", "COMPLETED, COMPLETED", "FAILED, FAILED"})
  void shouldQueryByState(
      final BatchOperationItemState apiState, final OperationState backendState) {
    // given
    final var filter = FilterBuilders.batchOperationItem(f -> f.states(apiState.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo(backendState.name());
            });
  }

  @Test
  void shouldQueryByMultipleState() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f ->
                f.states(
                    BatchOperationItemState.ACTIVE.name(),
                    BatchOperationItemState.COMPLETED.name(),
                    BatchOperationItemState.FAILED.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.values())
                  .containsExactlyInAnyOrder(
                      TypedValue.of(OperationState.SCHEDULED.name()),
                      TypedValue.of(OperationState.COMPLETED.name()),
                      TypedValue.of(OperationState.FAILED.name()));
            });
  }

  @Test
  void shouldQueryByStateNegate() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f -> f.stateOperations(Operation.neq(BatchOperationItemState.COMPLETED.name())));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            t -> {
              assertThat(t.mustNot()).isNotEmpty();
            });
    final var mustNotQuery = ((SearchBoolQuery) queryVariant).mustNot().get(0).queryOption();
    assertThat(mustNotQuery)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo(OperationState.COMPLETED.name());
            });
  }

  @Test
  void shouldQueryByAllFields() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f ->
                f.batchOperationKeys("123")
                    .states("ACTIVE")
                    .itemKeys(123L)
                    .processInstanceKeys(456L)
                    .operationTypes(BatchOperationType.CANCEL_PROCESS_INSTANCE.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(5);

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
              assertThat(t.value().stringValue()).isEqualTo("SCHEDULED");
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

    assertThat(((SearchBoolQuery) queryVariant).must().get(4).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue())
                  .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE.name());
            });
  }

  @Test
  void shouldQueryByOperationType() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f -> f.operationTypes(BatchOperationType.CANCEL_PROCESS_INSTANCE.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue())
                  .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE.name());
            });
  }

  @Test
  void shouldQueryByMultipleOperationTypes() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f ->
                f.operationTypes(
                    BatchOperationType.CANCEL_PROCESS_INSTANCE.name(),
                    BatchOperationType.RESOLVE_INCIDENT.name(),
                    BatchOperationType.DELETE_PROCESS_INSTANCE.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.values())
                  .containsExactlyInAnyOrder(
                      TypedValue.of(BatchOperationType.CANCEL_PROCESS_INSTANCE.name()),
                      TypedValue.of(BatchOperationType.RESOLVE_INCIDENT.name()),
                      TypedValue.of(BatchOperationType.DELETE_PROCESS_INSTANCE.name()));
            });
  }

  @Test
  void shouldQueryByOperationTypeNegate() {
    // given
    final var filter =
        FilterBuilders.batchOperationItem(
            f ->
                f.operationTypeOperations(
                    Operation.neq(BatchOperationType.CANCEL_PROCESS_INSTANCE.name())));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            t -> {
              assertThat(t.mustNot()).isNotEmpty();
            });
    final var mustNotQuery = ((SearchBoolQuery) queryVariant).mustNot().get(0).queryOption();
    assertThat(mustNotQuery)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue())
                  .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE.name());
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
        transformQuery(FilterBuilders.batchOperationItem(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.batchOperationItem(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.batchOperationItem(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }
}
