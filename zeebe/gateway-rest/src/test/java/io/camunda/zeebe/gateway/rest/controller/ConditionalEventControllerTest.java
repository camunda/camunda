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

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ConditionalEventServices;
import io.camunda.service.ConditionalEventServices.ConditionalEventCreateRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = ConditionalEventController.class)
public class ConditionalEventControllerTest extends RestControllerTest {

  static final String PROCESS_INSTANCES_START_URL = "/v2/conditions";

  @MockitoBean ConditionalEventServices conditionalEventServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(conditionalEventServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(conditionalEventServices);
  }

  @Test
  void shouldRejectConditionalEventTriggerIfTenantWithoutMultiTenancy() {
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
            "detail":"Expected to handle request Trigger Conditional Event with tenant identifier 'tenantId', but multi-tenancy is disabled",
            "instance":"/v2/conditions"
         }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
  void shouldRejectConditionalEventTriggerIfNoTenantWithMultiTenancy() {
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
            "detail":"Expected to handle request Trigger Conditional Event with tenant identifiers [], but no tenant identifier was provided.",
            "instance":"/v2/conditions"
         }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
  void shouldRejectConditionalEventTriggerIfServiceFails() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var expectedError = "This is an expected error";

    when(conditionalEventServices.triggerConditionalEvent(any(ConditionalEventCreateRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException(expectedError)));

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "tenantId": "tenantId"
        }""";

    final var expectedBody =
        String.format(
            """
        {
            "type":"about:blank",
            "title":"Bad Request",
            "status":400,
            "detail":"%s",
            "instance":"/v2/conditions"
         }""",
            expectedError);

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
  void shouldRejectConditionalEventTriggerIfInvalidInput() {
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
            "instance":"/v2/conditions"
         }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
  void shouldRejectConditionalEventTriggerIfMissingPermissions() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    when(conditionalEventServices.triggerConditionalEvent(any(ConditionalEventCreateRequest.class)))
        .thenThrow(
            ErrorMapper.createForbiddenException(
                Authorization.of(a -> a.processDefinition().createProcessInstance())));

    final var request =
        """
        {
            "processDefinitionKey": "123"
        }
        """;

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"FORBIDDEN",
            "status":403,
            "detail":"Unauthorized to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'",
            "instance":"/v2/conditions"
         }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
}
