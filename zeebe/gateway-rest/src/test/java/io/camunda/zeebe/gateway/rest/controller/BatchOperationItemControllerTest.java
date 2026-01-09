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

import io.camunda.gateway.protocol.model.BatchOperationItemStateEnum;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = BatchOperationItemsController.class)
class BatchOperationItemControllerTest extends RestControllerTest {

  @MockitoBean private BatchOperationServices batchOperationServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(batchOperationServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(batchOperationServices);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    keyOperationTestCases(
        streamBuilder,
        "itemKey",
        ops ->
            new io.camunda.search.filter.BatchOperationItemFilter.Builder()
                .itemKeyOperations(ops)
                .build());
    keyOperationTestCases(
        streamBuilder,
        "processInstanceKey",
        ops ->
            new io.camunda.search.filter.BatchOperationItemFilter.Builder()
                .processInstanceKeyOperations(ops)
                .build());
    basicStringOperationTestCases(
        streamBuilder,
        "batchOperationKey",
        ops ->
            new io.camunda.search.filter.BatchOperationItemFilter.Builder()
                .batchOperationKeyOperations(ops)
                .build());
    customOperationTestCases(
        streamBuilder,
        "state",
        ops ->
            new io.camunda.search.filter.BatchOperationItemFilter.Builder()
                .stateOperations(ops)
                .build(),
        List.of(
            List.of(Operation.eq(String.valueOf(BatchOperationItemStateEnum.ACTIVE))),
            List.of(Operation.neq(String.valueOf(BatchOperationItemStateEnum.COMPLETED))),
            List.of(
                Operation.in(
                    String.valueOf(BatchOperationItemStateEnum.COMPLETED),
                    String.valueOf(BatchOperationItemStateEnum.ACTIVE)),
                Operation.like("act"))),
        true);

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchBatchOperationItemsWithAdvancedFilter(
      final String filterString, final io.camunda.search.filter.BatchOperationItemFilter filter) {
    // given
    final var entity = getBatchOperationItemEntity("1");
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);

    // when / then
    when(batchOperationServices.searchItems(any(BatchOperationItemQuery.class)))
        .thenReturn(new SearchQueryResult(1, false, List.of(entity), null, null));

    webClient
        .post()
        .uri("/v2/batch-operation-items/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                 {
                    "items":
                    [
                        {
                            "batchOperationKey":"1",
                            "operationType":"CANCEL_PROCESS_INSTANCE",
                            "itemKey":"11",
                            "processInstanceKey":"12",
                            "state":"FAILED",
                            "processedDate":"2025-03-18T10:57:44.000+01:00",
                            "errorMessage": "error"
                        }
                    ],
                    "page":{
                      "totalItems":1,
                      "hasMoreTotalItems": false
                    }
                }""",
            JsonCompareMode.STRICT);

    verify(batchOperationServices)
        .searchItems(new BatchOperationItemQuery.Builder().filter(filter).build());
  }

  private static BatchOperationItemEntity getBatchOperationItemEntity(
      final String batchOperationKey) {
    return new BatchOperationEntity.BatchOperationItemEntity(
        batchOperationKey,
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        11L,
        12L,
        13L,
        BatchOperationItemState.FAILED,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        "error");
  }
}
