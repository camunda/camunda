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
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemFilter;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemSearchQuery;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = BatchOperationItemsController.class)
class BatchOperationItemControllerTest extends RestControllerTest {

  @MockitoBean private BatchOperationServices batchOperationServices;

  @BeforeEach
  void setUpServices() {
    when(batchOperationServices.withAuthentication(any(Authentication.class)))
        .thenReturn(batchOperationServices);
  }

  @Test
  void shouldSearchBatchOperationItems() {
    final var entity = getBatchOperationItemEntity("1");
    when(batchOperationServices.searchItems(any(BatchOperationItemQuery.class)))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null, null));

    final var searchQuery =
        new BatchOperationItemSearchQuery()
            .filter(
                new BatchOperationItemFilter().state(BatchOperationItemFilter.StateEnum.ACTIVE));

    webClient
        .post()
        .uri("/v2/batch-operation-items/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchQuery)
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
                            "batchOperationId":"1",
                            "itemKey":"11",
                            "processInstanceKey":"12",
                            "state":"FAILED",
                            "processedDate":"2025-03-18T10:57:44.000+01:00",
                            "errorMessage": "error"
                        }
                    ],
                    "page":{"totalItems":1,"firstSortValues":[],"lastSortValues":[]}
                }""");
  }

  private static BatchOperationItemEntity getBatchOperationItemEntity(
      final String batchOperationId) {
    return new BatchOperationEntity.BatchOperationItemEntity(
        batchOperationId,
        11L,
        12L,
        BatchOperationItemState.FAILED,
        OffsetDateTime.parse("2025-03-18T10:57:44+01:00"),
        "error");
  }
}
