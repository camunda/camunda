/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ExpressionServices;
import io.camunda.service.ExpressionServices.ExpressionEvaluationRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = ExpressionController.class)
public class ExpressionControllerTest extends RestControllerTest {

  static final String EXPRESSION_URL = "/v2/expression/evaluation";

  @Captor ArgumentCaptor<ExpressionEvaluationRequest> requestCaptor;
  @MockitoBean ExpressionServices expressionServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean MultiTenancyConfiguration multiTenancyConfiguration;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(expressionServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(expressionServices);
    when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);
  }

  @Test
  void shouldEvaluateExpression() {
    // given
    final var expressionRecord = mock(ExpressionRecord.class);
    when(expressionRecord.getExpression()).thenReturn("=x + y");
    when(expressionRecord.getResultValue()).thenReturn("10");
    when(expressionRecord.getWarnings()).thenReturn(List.of());

    when(expressionServices.evaluateExpression(any(ExpressionEvaluationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(expressionRecord));

    final var request =
        """
        {
            "expression": "=x + y",
            "tenantId": "tenant1"
        }""";

    // when / then
    webClient
        .post()
        .uri(EXPRESSION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
                "expression": "=x + y",
                "result": "10",
                "warnings": []
            }""",
            JsonCompareMode.STRICT);

    verify(expressionServices).evaluateExpression(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.expression()).isEqualTo("=x + y");
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldRejectEvaluationWithMissingExpression() {
    // given
    final var request =
        """
        {
            "tenantId": "tenant1"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No expression provided",
              "instance": "%s"
            }"""
            .formatted(EXPRESSION_URL);

    // when / then
    webClient
        .post()
        .uri(EXPRESSION_URL)
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
  void shouldRejectEvaluationWithInvalidTenantId() {
    // given
    final var request =
        """
        {
            "expression": "=x + y",
            "tenantId": "$tenant1"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "Expected to handle request Expression Evaluation with tenant identifier '$tenant1', but tenant identifier contains illegal characters.",
              "instance": "%s"
            }"""
            .formatted(EXPRESSION_URL);

    // when / then
    webClient
        .post()
        .uri(EXPRESSION_URL)
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
