/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(DecisionDefinitionController.class)
public class DecisionDefinitionControllerTest extends RestControllerTest {

  private static final String DECISION_BASE_URL = "/v2/decision-definitions";
  private static final String EVALUATION_URL = DECISION_BASE_URL + "/evaluation";
  private static final String EXPECTED_EVALUATION_RESPONSE =
      """
          {
             "decisionDefinitionKey":"123456",
             "decisionDefinitionId":"decisionId",
             "decisionDefinitionName":"decisionName",
             "decisionDefinitionVersion":1,
             "decisionRequirementsId":"decisionRequirementsId",
             "decisionRequirementsKey":"123456",
             "output":"null",
             "failedDecisionDefinitionId":"",
             "failureMessage":"",
             "tenantId":"tenantId",
             "decisionInstanceKey":"123",
             "decisionEvaluationKey":"123",
             "evaluatedDecisions":[]
          }""";

  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean private DecisionDefinitionServices decisionServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(decisionServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(decisionServices);
  }

  @Test
  void shouldEvaluateDecisionWithDecisionKey() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString()))
        .thenReturn((buildResponse("tenantId")));

    final var request =
        """
            {
              "decisionDefinitionKey": 123456,
              "variables": {
                "key": "value"
              },
              "tenantId": "tenantId"
            }""";

    // when/then
    final ResponseSpec response =
        webClient
            .post()
            .uri(EVALUATION_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response.expectBody().json(EXPECTED_EVALUATION_RESPONSE, JsonCompareMode.STRICT);
    Mockito.verify(decisionServices)
        .evaluateDecision("", 123456L, Map.of("key", "value"), "tenantId");
  }

  @Test
  void shouldEvaluateDecisionWithMultitenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString()))
        .thenReturn((buildResponse(TenantOwned.DEFAULT_TENANT_IDENTIFIER)));

    final var request =
        """
            {
              "decisionDefinitionKey": 123456,
              "variables": {
                "key": "value"
              },
              "tenantId": "<default>"
            }""";

    // when/then
    webClient
        .post()
        .uri(EVALUATION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();
    Mockito.verify(decisionServices)
        .evaluateDecision(
            "", 123456L, Map.of("key", "value"), TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldEvaluateDecisionWithDecisionId() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString()))
        .thenReturn((buildResponse("tenantId")));

    final var request =
        """
            {
              "decisionDefinitionId": "decisionId",
              "variables": {
                "key": "value"
              },
              "tenantId": "tenantId"
            }""";

    // when/then
    final ResponseSpec response =
        webClient
            .post()
            .uri(EVALUATION_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response.expectBody().json(EXPECTED_EVALUATION_RESPONSE, JsonCompareMode.STRICT);
    Mockito.verify(decisionServices)
        .evaluateDecision("decisionId", -1L, Map.of("key", "value"), "tenantId");
  }

  @Test
  void shouldRejectEvaluateDecisionWithDecisionIdAndDecisionKey() {
    // given
    final var request =
        """
            {
              "decisionDefinitionId": "decisionId",
              "decisionDefinitionKey": 123456,
              "variables": {
                "key": "value"
              }
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"Only one of [decisionDefinitionId, decisionDefinitionKey] is allowed.",
                "instance":"/v2/decision-definitions/evaluation"
             }""";

    // when then
    webClient
        .post()
        .uri(EVALUATION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectEvaluateDecisionWithoutDecisionIdAndDecisionKey() {
    // given
    final var request =
        """
            {
              "variables": {
                "key": "value"
              }
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of [decisionDefinitionId, decisionDefinitionKey] is required.",
                "instance":"/v2/decision-definitions/evaluation"
             }""";

    // when then
    webClient
        .post()
        .uri(EVALUATION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  private CompletableFuture<BrokerResponse<DecisionEvaluationRecord>> buildResponse(
      final String tenantId) {
    final var record =
        new DecisionEvaluationRecord()
            .setDecisionId("decisionId")
            .setDecisionKey(123456L)
            .setDecisionName("decisionName")
            .setDecisionVersion(1)
            .setDecisionRequirementsId("decisionRequirementsId")
            .setDecisionRequirementsKey(123456L)
            .setTenantId(tenantId);
    return CompletableFuture.completedFuture(new BrokerResponse<>(record, 1, 123));
  }
}
