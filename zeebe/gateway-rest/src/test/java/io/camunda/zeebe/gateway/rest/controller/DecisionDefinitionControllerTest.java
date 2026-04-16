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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultDecisionDefinitionServiceAdapter;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@Import(DefaultDecisionDefinitionServiceAdapter.class)
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
             "failedDecisionDefinitionId":null,
             "failureMessage":null,
             "tenantId":"tenantId",
             "decisionInstanceKey":"123",
             "decisionEvaluationKey":"123",
             "evaluatedDecisions":[
               {
                 "decisionDefinitionKey":"400",
                 "decisionDefinitionId":"evalDecisionId",
                 "decisionEvaluationInstanceKey":"",
                 "decisionDefinitionName":"evalDecisionName",
                 "decisionDefinitionVersion":1,
                 "decisionDefinitionType":"DECISION_TABLE",
                 "output":"null",
                 "tenantId":"tenantId",
                 "matchedRules":[],
                 "evaluatedInputs":[]
               }
             ]
          }""";

  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean private DecisionDefinitionServices decisionServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldEvaluateDecisionWithDecisionKey() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString(), any()))
        .thenReturn((buildResponse("tenantId")));

    final var request =
        """
            {
              "decisionDefinitionKey": "123456",
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
        .evaluateDecision(
            eq(""), eq(123456L), eq(Map.<String, Object>of("key", "value")), eq("tenantId"), any());
  }

  @Test
  void shouldEvaluateDecisionWithMultitenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString(), any()))
        .thenReturn((buildResponse(TenantOwned.DEFAULT_TENANT_IDENTIFIER)));

    final var request =
        """
            {
              "decisionDefinitionKey": "123456",
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
            eq(""),
            eq(123456L),
            eq(Map.<String, Object>of("key", "value")),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            any());
  }

  @Test
  void shouldEvaluateDecisionWithDecisionId() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(decisionServices.evaluateDecision(anyString(), anyLong(), anyMap(), anyString(), any()))
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
        .evaluateDecision(
            eq("decisionId"),
            eq(-1L),
            eq(Map.<String, Object>of("key", "value")),
            eq("tenantId"),
            any());
  }

  @Test
  void shouldRejectEvaluateDecisionWithDecisionIdAndDecisionKey() {
    // given
    final var request =
        """
            {
              "decisionDefinitionId": "decisionId",
              "decisionDefinitionKey": "123456",
              "variables": {
                "key": "value"
              }
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Request property [decisionDefinitionKey] cannot be parsed",
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
                "title":"Bad Request",
                "status":400,
                "detail":"At least one of [decisionDefinitionId, decisionDefinitionKey] is required",
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

    final var evaluatedDecision = record.evaluatedDecisions().add();
    evaluatedDecision
        .setDecisionId("evalDecisionId")
        .setDecisionKey(400L)
        .setDecisionName("evalDecisionName")
        .setDecisionVersion(1)
        .setDecisionType("DECISION_TABLE")
        .setDecisionOutput(new UnsafeBuffer(MsgPackHelper.NIL))
        .setTenantId(tenantId);

    return CompletableFuture.completedFuture(new BrokerResponse<>(record, 1, 123));
  }
}
