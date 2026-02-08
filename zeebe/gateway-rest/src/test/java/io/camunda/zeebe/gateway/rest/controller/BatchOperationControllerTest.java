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

import io.camunda.gateway.protocol.model.BatchOperationStateEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationErrorEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.BatchOperationSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
              "actorType":"USER",
              "actorId":"frodo.baggins@fellowship",
              "operationsTotalCount":10,
              "operationsFailedCount":0,
              "operationsCompletedCount":10,
              "errors": []
          }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnPartiallyCompletedBatchOperation() {
    final var batchOperationKey = "1";
    final var batchOperationEntity =
        getFailedBatchOperationEntity(
            batchOperationKey, BatchOperationState.PARTIALLY_COMPLETED, 10, 0, 10);

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
              "actorType":"USER",
              "actorId":"frodo.baggins@fellowship",
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

  @Test
  void shouldReturnFailedBatchOperation() {
    final var batchOperationKey = "1";
    final var batchOperationEntity =
        getFailedBatchOperationEntity(batchOperationKey, BatchOperationState.FAILED, 0, 0, 0);

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
              "state":"FAILED",
              "batchOperationType":"CANCEL_PROCESS_INSTANCE",
              "startDate":"2025-03-18T10:57:44.000+01:00",
              "endDate":"2025-03-18T10:57:45.000+01:00",
              "actorType":"USER",
              "actorId":"frodo.baggins@fellowship",
              "operationsTotalCount":0,
              "operationsFailedCount":0,
              "operationsCompletedCount":0,
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
    customOperationTestCases(
        streamBuilder,
        "actorType",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .actorTypeOperations(ops)
                .build(),
        List.of(
            List.of(Operation.eq(String.valueOf(AuditLogActorType.USER))),
            List.of(Operation.eq(String.valueOf(AuditLogActorType.CLIENT)))),
        true);

    customOperationTestCases(
        streamBuilder,
        "actorId",
        ops ->
            new io.camunda.search.filter.BatchOperationFilter.Builder()
                .actorIdOperations(ops)
                .build(),
        List.of(
            List.of(Operation.eq("frodo.baggins@fellowship")),
            List.of(Operation.neq("aragorn@fellowship")),
            List.of(Operation.in("frodo@fellowship", "aragorn@fellowship")),
            List.of(Operation.notIn("samwise@fellowship", "gandalf@fellowship")),
            List.of(Operation.exists(true)),
            List.of(Operation.exists(false)),
            List.of(Operation.like("fro*"))),
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
        .thenReturn(new SearchQueryResult<>(1, false, List.of(entity), null, null));

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
                "actorType": "USER",
                "actorId": "frodo.baggins@fellowship",
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

  private static Stream<Arguments> provideSortParameters() {
    final var entityWithEarlyStart =
        getBatchOperationEntityWithStartDate(
            "1", OffsetDateTime.parse("2025-03-18T10:57:43+01:00"));
    final var entityWithLateStart =
        getBatchOperationEntityWithStartDate(
            "2", OffsetDateTime.parse("2025-03-18T10:57:45+01:00"));
    final var entityAragorn = getBatchOperationEntityWithActorId("3", "aragorn@fellowship");
    final var entityFrodo = getBatchOperationEntityWithActorId("4", "frodo@fellowship");
    final var entityActorClient =
        getBatchOperationEntityWithActorType("5", AuditLogActorType.CLIENT);
    final var entityActorUser = getBatchOperationEntityWithActorType("6", AuditLogActorType.USER);

    return Stream.of(
        Arguments.of(
            BatchOperationSort.of(s -> s.startDate().asc()),
            List.of(entityWithEarlyStart, entityWithLateStart),
            List.of("2025-03-18T10:57:43.000+01:00", "2025-03-18T10:57:45.000+01:00")),
        Arguments.of(
            BatchOperationSort.of(s -> s.startDate().desc()),
            List.of(entityWithLateStart, entityWithEarlyStart),
            List.of("2025-03-18T10:57:45.000+01:00", "2025-03-18T10:57:43.000+01:00")),
        Arguments.of(
            BatchOperationSort.of(s -> s.actorId().asc()),
            List.of(entityAragorn, entityFrodo),
            List.of("aragorn@fellowship", "frodo@fellowship")),
        Arguments.of(
            BatchOperationSort.of(s -> s.actorId().desc()),
            List.of(entityFrodo, entityAragorn),
            List.of("frodo@fellowship", "aragorn@fellowship")),
        Arguments.of(
            BatchOperationSort.of(s -> s.actorType().asc()),
            List.of(entityActorClient, entityActorUser),
            List.of("CLIENT", "USER")),
        Arguments.of(
            BatchOperationSort.of(s -> s.actorType().desc()),
            List.of(entityActorUser, entityActorClient),
            List.of("USER", "CLIENT")));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  void shouldSearchBatchOperationsSorted(
      final BatchOperationSort sort,
      final List<BatchOperationEntity> searchResultItems,
      final List<String> expectedOrder) {
    // given
    final var fieldSortings = sort.getFieldSortings();
    Assertions.assertThat(fieldSortings).as("Test assumes exactly one sort entry").hasSize(1);

    final var fieldSorting = fieldSortings.getFirst();
    final var sortedByField = fieldSorting.field();
    final var order = fieldSorting.order().value();

    final var request =
        """
        { "sort": [{ "field": "%s", "order": "%s" }] }"""
            .formatted(sortedByField, order);

    when(batchOperationServices.search(any(BatchOperationQuery.class)))
        .thenReturn(
            new SearchQueryResult<>(
                searchResultItems.size(), false, searchResultItems, null, null));

    // when
    webClient
        .post()
        .uri("/v2/batch-operations/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0].%s".formatted(sortedByField))
        .isEqualTo(expectedOrder.get(0))
        .jsonPath("$.items[1].%s".formatted(sortedByField))
        .isEqualTo(expectedOrder.get(1));

    // then
    verify(batchOperationServices).search(new BatchOperationQuery.Builder().sort(sort).build());
  }

  private static BatchOperationEntity getBatchOperationEntity(final String batchOperationKey) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        AuditLogActorType.USER,
        "frodo.baggins@fellowship",
        10,
        0,
        10,
        emptyList());
  }

  private static BatchOperationEntity getBatchOperationEntityWithStartDate(
      final String batchOperationKey, final OffsetDateTime startDate) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        startDate,
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        AuditLogActorType.USER,
        "frodo@fellowship",
        10,
        0,
        10,
        emptyList());
  }

  private static BatchOperationEntity getBatchOperationEntityWithActorId(
      final String batchOperationKey, final String actorId) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        AuditLogActorType.USER,
        actorId,
        10,
        0,
        10,
        emptyList());
  }

  private static BatchOperationEntity getBatchOperationEntityWithActorType(
      final String batchOperationKey, final AuditLogActorType actorType) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        actorType,
        "frodo@fellowship",
        10,
        0,
        10,
        emptyList());
  }

  private static BatchOperationEntity getFailedBatchOperationEntity(
      final String batchOperationKey,
      final BatchOperationState batchOperationState,
      final int operationsTotalCount,
      final int operationsFailedCount,
      final int operationsCompletedCount) {
    return new BatchOperationEntity(
        batchOperationKey,
        batchOperationState,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        AuditLogActorType.USER,
        "frodo.baggins@fellowship",
        operationsTotalCount,
        operationsFailedCount,
        operationsCompletedCount,
        List.of(
            new BatchOperationErrorEntity(1, "QUERY_FAILED", "Stack Trace"),
            new BatchOperationErrorEntity(2, "QUERY_FAILED", "Stack Trace")));
  }
}
