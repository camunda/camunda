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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ConditionalEventServices;
import io.camunda.service.ConditionalEventServices.ConditionalEventCreateRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = ConditionalEventController.class)
public class ConditionalEventControllerTest extends RestControllerTest {

  static final String CONDITIONAL_EVENT_URL = "/v2/conditionals";

  @MockitoBean ConditionalEventServices conditionalEventServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @Captor ArgumentCaptor<ConditionalEventCreateRequest> requestCaptor;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(conditionalEventServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(conditionalEventServices);
  }

  @Test
  void shouldEvaluateConditionalStartEvents() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    final var mockResponse = new ConditionalEvaluationRecord();
    mockResponse.addStartedProcessInstance(2251799813685249L, 2251799813685250L);
    mockResponse.addStartedProcessInstance(2251799813685251L, 2251799813685252L);

    when(conditionalEventServices.evaluateConditionalEvent(
            any(ConditionalEventCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "variables": {
                "x": 100,
                "y": 50
            }
        }""";

    final var expectedResponse =
        """
        {
            "processInstances": [
                {
                    "processDefinitionKey": "2251799813685249",
                    "processInstanceKey": "2251799813685250"
                },
                {
                    "processDefinitionKey": "2251799813685251",
                    "processInstanceKey": "2251799813685252"
                }
            ]
        }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(conditionalEventServices).evaluateConditionalEvent(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.variables()).containsEntry("x", 100).containsEntry("y", 50);
  }

  @Test
  void shouldRejectConditionalEventEvaluationIfTenantWithoutMultiTenancy() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "tenantId": "tenantId"
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"Expected to handle request Evaluate Conditional Event with tenant identifier 'tenantId', but multi-tenancy is disabled",
            "instance":"/v2/conditionals"
         }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
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
  void shouldRejectConditionalEventEvaluationIfNoTenantWithMultiTenancy() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var request =
        """
        {
            "processDefinitionKey": "123"
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"Expected to handle request Evaluate Conditional Event with tenant identifiers [], but no tenant identifier was provided.",
            "instance":"/v2/conditionals"
         }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
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
  void shouldRejectConditionalEventEvaluationIfServiceFails() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var expectedError = "This is an expected error";

    when(conditionalEventServices.evaluateConditionalEvent(
            any(ConditionalEventCreateRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(expectedError, ServiceException.Status.INVALID_ARGUMENT)));

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "tenantId": "tenantId",
            "variables": {"x": 1}
        }""";

    final var expectedBody =
        String.format(
            """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"%s",
            "instance":"/v2/conditionals"
         }""",
            expectedError);

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
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
  void shouldRejectConditionalEventEvaluationIfInvalidInput() {
    // given
    final var request =
        """
        {
            "processDefinitionKey": "123",
            "tenantId": "tenantId",
            "unexpectedField": "unexpectedValue"
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"Bad Request",
            "status":400,
            "detail":"Request property [unexpectedField] cannot be parsed",
            "instance":"/v2/conditionals"
         }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
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
  void shouldRejectConditionalEventEvaluationIfMissingPermissions() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    when(conditionalEventServices.evaluateConditionalEvent(
            any(ConditionalEventCreateRequest.class)))
        .thenThrow(
            ErrorMapper.createForbiddenException(
                Authorization.of(a -> a.processDefinition().createProcessInstance())));

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "variables": {"x": 1}
        }
        """;

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"FORBIDDEN",
            "status":403,
            "detail":"Unauthorized to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'",
            "instance":"/v2/conditionals"
         }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null"})
  void shouldRejectConditionalEventEvaluationIfVariablesMissing(final String variablesValue) {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "variables": %s
        }"""
            .formatted(variablesValue);

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"No variables provided.",
            "instance":"/v2/conditionals"
         }""";

    // when / then
    webClient
        .post()
        .uri(CONDITIONAL_EVENT_URL)
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
