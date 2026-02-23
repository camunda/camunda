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

import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = DecisionInstanceController.class)
public class DecisionInstanceControllerTest extends RestControllerTest {

  static final String DECISION_INSTANCES_BASE_URL = "/v2/decision-instances";
  static final String DELETE_DECISION_URL = DECISION_INSTANCES_BASE_URL + "/%d/deletion";

  @MockitoBean private DecisionInstanceServices decisionInstanceServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(decisionInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(decisionInstanceServices);
  }

  @Test
  void shouldDeleteDecisionInstance() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance(1L, 123L))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "operationReference": 123
            }""";

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted(1L))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(decisionInstanceServices).deleteDecisionInstance(1L, 123L);
  }

  @Test
  void shouldDeleteDecisionInstanceWithNoBody() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance(2L, null))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted(2L))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(decisionInstanceServices).deleteDecisionInstance(2L, null);
  }

  @Test
  void shouldDeleteDecisionInstanceWithEmptyBody() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance(3L, null))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
        {}""";

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted(3L))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(decisionInstanceServices).deleteDecisionInstance(3L, null);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceOnDecisionInstanceNotFound() {
    // given
    when(decisionInstanceServices.deleteDecisionInstance(999L, null))
        .thenReturn(
            CompletableFuture.failedFuture(
                new io.camunda.service.exception.ServiceException(
                    "Decision Instance with key '999-999' not found",
                    io.camunda.service.exception.ServiceException.Status.NOT_FOUND)));

    final var expectedBody =
        """
            {
                "type": "about:blank",
                "title": "NOT_FOUND",
                "status": 404,
                "detail": "Decision Instance with key '999-999' not found",
                "instance": "%s"
            }"""
            .formatted(DELETE_DECISION_URL.formatted(999L));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted(999L))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance(999L, null);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceOnForbidden() {
    // given
    when(decisionInstanceServices.deleteDecisionInstance(4L, null))
        .thenReturn(
            CompletableFuture.failedFuture(
                new io.camunda.service.exception.ServiceException(
                    "Unauthorized to perform operation 'DELETE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'",
                    io.camunda.service.exception.ServiceException.Status.FORBIDDEN)));

    final var expectedBody =
        """
            {
                "type": "about:blank",
                "title": "FORBIDDEN",
                "status": 403,
                "detail": "Unauthorized to perform operation 'DELETE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'",
                "instance": "%s"
            }"""
            .formatted(DELETE_DECISION_URL.formatted(4L));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted(4L))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance(4L, null);
  }

  @Test
  void shouldDeleteDecisionInstanceBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstancesBatchOperation(
            any(DecisionInstanceFilter.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "filter":
               {
                  "decisionDefinitionId": "test-decision-definition-id"
                }
            }""";

    // when / then
    webClient
        .post()
        .uri("/v2/decision-instances/deletion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
          {"batchOperationKey":"123","batchOperationType":"DELETE_DECISION_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(decisionInstanceServices)
        .deleteDecisionInstancesBatchOperation(any(DecisionInstanceFilter.class));
  }

  @Test
  void shouldRejectDeleteDecisionInstanceBatchOperationWithNoRequestBody() {
    // given
    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Required request body is missing",
                "instance":"/v2/decision-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/decision-instances/deletion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceBatchOperationWithEmptyRequestBody() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/decision-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/decision-instances/deletion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceBatchOperationWithEmptyFilter() {
    // given
    final var request =
        """
        {
          "filter": {}
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of filter criteria is required.",
                "instance":"/v2/decision-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/decision-instances/deletion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }
}
