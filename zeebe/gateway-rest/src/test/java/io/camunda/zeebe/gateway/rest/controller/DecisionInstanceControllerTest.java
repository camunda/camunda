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

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = DecisionInstanceController.class)
public class DecisionInstanceControllerTest extends RestControllerTest {

  static final String DECISION_INSTANCES_BASE_URL = "/v2/decision-instances";
  static final String DELETE_DECISION_URL = DECISION_INSTANCES_BASE_URL + "/%s/deletion";

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
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance("1-1", 123L))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "operationReference": 123
            }""";

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("1-1"))
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

    verify(decisionInstanceServices).deleteDecisionInstance("1-1", 123L);
  }

  @Test
  void shouldDeleteDecisionInstanceWithNoBody() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(456L);
    record.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance("2-2", null))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("2-2"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
          {"batchOperationKey":"456","batchOperationType":"DELETE_DECISION_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance("2-2", null);
  }

  @Test
  void shouldDeleteDecisionInstanceWithEmptyBody() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(789L);
    record.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);

    when(decisionInstanceServices.deleteDecisionInstance("3-3", null))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
        {}""";

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("3-3"))
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
          {"batchOperationKey":"789","batchOperationType":"DELETE_DECISION_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance("3-3", null);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceOnDecisionInstanceNotFound() {
    // given
    when(decisionInstanceServices.deleteDecisionInstance("999-999", null))
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
            .formatted(DELETE_DECISION_URL.formatted("999-999"));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("999-999"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance("999-999", null);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceOnForbidden() {
    // given
    when(decisionInstanceServices.deleteDecisionInstance("4-4", null))
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
            .formatted(DELETE_DECISION_URL.formatted("4-4"));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("4-4"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance("4-4", null);
  }

  @Test
  void shouldRejectDeleteDecisionInstanceOnConflict() {
    // given
    when(decisionInstanceServices.deleteDecisionInstance("5-5", null))
        .thenReturn(
            CompletableFuture.failedFuture(
                new io.camunda.service.exception.ServiceException(
                    "Decision Instance with key '5-5' is not in a completed or terminated state and cannot be deleted",
                    io.camunda.service.exception.ServiceException.Status.INVALID_STATE)));

    final var expectedBody =
        """
            {
                "type": "about:blank",
                "title": "INVALID_STATE",
                "status": 409,
                "detail": "Decision Instance with key '5-5' is not in a completed or terminated state and cannot be deleted",
                "instance": "%s"
            }"""
            .formatted(DELETE_DECISION_URL.formatted("5-5"));

    // when / then
    webClient
        .post()
        .uri(DELETE_DECISION_URL.formatted("5-5"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(decisionInstanceServices).deleteDecisionInstance("5-5", null);
  }
}
