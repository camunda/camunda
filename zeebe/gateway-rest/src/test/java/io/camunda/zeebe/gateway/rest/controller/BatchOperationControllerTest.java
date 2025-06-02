/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = BatchOperationController.class)
class BatchOperationControllerTest extends RestControllerTest {

  @MockitoBean private BatchOperationServices batchOperationServices;

  @BeforeEach
  void setUpServices() {
    when(batchOperationServices.withAuthentication(any(Authentication.class)))
        .thenReturn(batchOperationServices);
  }

  @Test
  void shouldReturnBatchOperation() {
    final var batchOperationId = "1";
    final var batchOperationEntity = getBatchOperationEntity(batchOperationId);

    when(batchOperationServices.getById(batchOperationId)).thenReturn(batchOperationEntity);

    webClient
        .get()
        .uri("/v2/batch-operations/{batchOperationKey}", batchOperationId)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {
              "batchOperationId":"1",
              "state":"COMPLETED",
              "batchOperationType":"CANCEL_PROCESS_INSTANCE",
              "startDate":"2025-03-18T10:57:44.000+01:00",
              "endDate":"2025-03-18T10:57:45.000+01:00",
              "operationsTotalCount":10,
              "operationsFailedCount":0,
              "operationsCompletedCount":10
          }""");
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    stringOperationTestCases(
        streamBuilder,
        "batchOperationId",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .batchOperationIdOperations(ops)
                .build());
    customOperationTestCases(
        streamBuilder,
        "operationType",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .operationTypeOperations(ops)
                .build(),
        List.of(
            List.of(Operation.eq(String.valueOf(BatchOperationTypeEnum.CANCEL_PROCESS_INSTANCE))),
            List.of(Operation.neq(String.valueOf(BatchOperationTypeEnum.MIGRATE_PROCESS_INSTANCE))),
            List.of(
                Operation.in(
                    String.valueOf(BatchOperationTypeEnum.MIGRATE_PROCESS_INSTANCE),
                    String.valueOf(BatchOperationTypeEnum.CANCEL_PROCESS_INSTANCE)),
                Operation.like("act"))),
        true);
    customOperationTestCases(
        streamBuilder,
        "state",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .stateOperations(ops)
                .build(),
        List.of(
            List.of(Operation.eq(String.valueOf(BatchOperationStateEnum.ACTIVE))),
            List.of(Operation.neq(String.valueOf(BatchOperationStateEnum.COMPLETED))),
            List.of(
                Operation.in(
                    String.valueOf(BatchOperationStateEnum.COMPLETED),
                    String.valueOf(BatchOperationStateEnum.ACTIVE)),
                Operation.like("act"))),
        true);

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchBatchOperationsWithAdvancedFilter(
      final String filterString, final io.camunda.search.filter.BatchOperationFilter filter) {
    // given
    final var entity = getBatchOperationEntity("1");
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);

    // when / then
    when(batchOperationServices.search(any(BatchOperationQuery.class)))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null, null));

    webClient
        .post()
        .uri("/v2/batch-operations/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {"items":[
          {
            "batchOperationId":"1",
            "state":"COMPLETED",
            "batchOperationType":"CANCEL_PROCESS_INSTANCE",
            "startDate":"2025-03-18T10:57:44.000+01:00",
            "endDate":"2025-03-18T10:57:45.000+01:00",
            "operationsTotalCount":10,
            "operationsFailedCount":0,
            "operationsCompletedCount":10
            }],
            "page":{"totalItems":1,"firstSortValues":[],"lastSortValues":[]}
           }""");

    verify(batchOperationServices).search(new BatchOperationQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldCancelBatchOperation() {
    final var batchOperationId = "1";
    when(batchOperationServices.cancel(batchOperationId))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/cancellation", batchOperationId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldSuspendBatchOperation() {
    final var batchOperationId = "1";
    when(batchOperationServices.suspend(batchOperationId))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/suspension", batchOperationId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldResumeBatchOperation() {
    final var batchOperationId = "1";
    when(batchOperationServices.resume(batchOperationId))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/resumption", batchOperationId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private static BatchOperationEntity getBatchOperationEntity(final String batchOperationId) {
    return new BatchOperationEntity(
        batchOperationId,
        BatchOperationState.COMPLETED,
        "CANCEL_PROCESS_INSTANCE",
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        10,
        0,
        10);
  }
}
