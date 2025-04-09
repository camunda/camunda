/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationFilter;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    final var batchOperationKey = 1L;
    final var batchOperationEntity = getBatchOperationEntity(batchOperationKey);

    when(batchOperationServices.getByKey(batchOperationKey)).thenReturn(batchOperationEntity);

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
              "batchOperationType":"PROCESS_CANCELLATION",
              "startDate":"2025-03-18T10:57:44.000+01:00",
              "endDate":"2025-03-18T10:57:45.000+01:00",
              "operationsTotalCount":10,
              "operationsFailedCount":0,
              "operationsCompletedCount":10
          }""");
  }

  @Test
  void shouldSearchBatchOperations() {
    final var searchQueryResult = new BatchOperationSearchQueryResult();
    final var entity = getBatchOperationEntity(1L);
    when(batchOperationServices.search(any(BatchOperationQuery.class)))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null, null));

    final var searchQuery =
        new BatchOperationSearchQuery()
            .filter(new BatchOperationFilter().state(BatchOperationFilter.StateEnum.ACTIVE));

    webClient
        .post()
        .uri("/v2/batch-operations/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchQuery)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {"items":[
          {
            "batchOperationKey":"1",
            "state":"COMPLETED",
            "batchOperationType":"PROCESS_CANCELLATION",
            "startDate":"2025-03-18T10:57:44.000+01:00",
            "endDate":"2025-03-18T10:57:45.000+01:00",
            "operationsTotalCount":10,
            "operationsFailedCount":0,
            "operationsCompletedCount":10
            }],
            "page":{"totalItems":1,"firstSortValues":[],"lastSortValues":[]}
           }""");
  }

  @Test
  void shouldCancelBatchOperation() {
    final var batchOperationKey = 1L;
    when(batchOperationServices.cancel(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/cancel", batchOperationKey)
        .exchange()
        .expectStatus()
        .isAccepted();
  }

  @Test
  void shouldPauseBatchOperation() {
    final var batchOperationKey = 1L;
    when(batchOperationServices.pause(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/pause", batchOperationKey)
        .exchange()
        .expectStatus()
        .isAccepted();
  }

  @Test
  void shouldResumeBatchOperation() {
    final var batchOperationKey = 1L;
    when(batchOperationServices.resume(batchOperationKey))
        .thenReturn(CompletableFuture.completedFuture(null));

    webClient
        .put()
        .uri("/v2/batch-operations/{key}/resume", batchOperationKey)
        .exchange()
        .expectStatus()
        .isAccepted();
  }

  @Test
  void shouldReturnBatchOperationItems() {
    final var batchOperationKey = 1L;

    final var batchOperationItem =
        new BatchOperationEntity.BatchOperationItemEntity(
            batchOperationKey, 1L, BatchOperationItemState.COMPLETED);
    when(batchOperationServices.getItemsByKey(batchOperationKey))
        .thenReturn(List.of(batchOperationItem));

    webClient
        .get()
        .uri("/v2/batch-operations/{batchOperationKey}/items", batchOperationKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"items":[
                {
                   "batchOperationKey":"1",
                   "itemKey":"1",
                   "state":"COMPLETED"}
                ]}
          """);
  }

  private static BatchOperationEntity getBatchOperationEntity(final long batchOperationKey) {
    return new BatchOperationEntity(
        batchOperationKey,
        BatchOperationState.COMPLETED,
        "PROCESS_CANCELLATION",
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        OffsetDateTime.parse("2025-03-18T10:57:45+01:00"),
        10,
        0,
        10);
  }
}
