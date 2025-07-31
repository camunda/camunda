/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationErrorEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
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
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = BatchOperationController.class)
class BatchOperationControllerTest extends RestControllerTest {

  @MockitoBean private BatchOperationServices batchOperationServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(batchOperationServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(batchOperationServices);
  }

  @Test
  void shouldReturnBatchOperation() {
    final var batchOperationKey = "1";
    final var batchOperationEntity = getBatchOperationEntity(batchOperationKey);

    when(batchOperationServices.getById(batchOperationKey)).thenReturn(batchOperationEntity);

    webClient
        .get()
        .uri("/v2/batch-operations/{batchOperationKey}", batchOperationKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {
              "batchOperationKey":"1",
              "state":"COMPLETED",
              "batchOperationType":"CANCEL_PROCESS_INSTANCE",
              "startDate":"2025-03-18T10:57:44.000+01:00",
              "endDate":"2025-03-18T10:57:45.000+01:00",
              "operationsTotalCount":10,
              "operationsFailedCount":0,
              "operationsCompletedCount":10,
              "errors": []
          }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnFailedBatchOperation() {
    final var batchOperationKey = "1";
    final var batchOperationEntity = getFailedBatchOperationEntity(batchOperationKey);

    when(batchOperationServices.getById(batchOperationKey)).thenReturn(batchOperationEntity);

    webClient
        .get()
        .uri("/v2/batch-operations/{batchOperationKey}", batchOperationKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {
              "batchOperationKey":"1",
              "state":"PARTIALLY_COMPLETED",
              "batchOperationType":"CANCEL_PROCESS_INSTANCE",
              "startDate":"2025-03-18T10:57:44.000+01:00",
              "endDate":"2025-03-18T10:57:45.000+01:00",
              "operationsTotalCount":10,
              "operationsFailedCount":0,
              "operationsCompletedCount":10,
              "errors":[
                {
                  "partitionId":1,
                  "type":"QUERY_FAILED",
                  "message":"Stack Trace"
                },
                {
                  "partitionId":2,
                  "type":"QUERY_FAILED",
                  "message":"Stack Trace"
                }
              ]
          }""",
            JsonCompareMode.STRICT);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    basicStringOperationTestCases(
        streamBuilder,
        "batchOperationKey",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .batchOperationKeyOperations(ops)
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
        .thenReturn(new SearchQueryResult(1, false, List.of(entity), null, null));

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
          {
            "items":[
              {
                "batchOperationKey": "1",
                "state": "COMPLETED",
                "batchOperationType": "CANCEL_PROCESS_INSTANCE",
                "startDate": "2025-03-18T10:57:44.000+01:00",
                "endDate": "2025-03-18T10:57:45.000+01:00",
                "operationsTotalCount": 10,
                "operationsFailedCount": 0,
                "operationsCompletedCount": 10,
                "errors": []
              }
            ],
            "page":{
              "totalItems": 1,
              "hasMoreTotalItems": false
             }
           }""",
            JsonCompareMode.STRICT);

    verify(batchOperationServices).search(new BatchOperationQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldCancelBatchOperation() {
    final var batchOperationKey = "1";
    when(batchOperationServices.cancel(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .post()
        .uri("/v2/batch-operations/{key}/cancellation", batchOperationKey)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldSuspendBatchOperation() {
    final var batchOperationKey = "1";
    when(batchOperationServices.suspend(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .post()
        .uri("/v2/batch-operations/{key}/suspension", batchOperationKey)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldResumeBatchOperation() {
    final var batchOperationKey = "1";
    when(batchOperationServices.resume(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .post()
        .uri("/v2/batch-operations/{key}/resumption", batchOperationKey)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private static BatchOperationEntity getBatchOperationEntity(final String batchOperationKey) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        10,
        0,
        10,
        emptyList());
  }

  private static BatchOperationEntity getFailedBatchOperationEntity(
      final String batchOperationKey) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.PARTIALLY_COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        10,
        0,
        10,
        List.of(
            new BatchOperationErrorEntity(1, "QUERY_FAILED", "Stack Trace"),
            new BatchOperationErrorEntity(2, "QUERY_FAILED", "Stack Trace")));
  }
}
