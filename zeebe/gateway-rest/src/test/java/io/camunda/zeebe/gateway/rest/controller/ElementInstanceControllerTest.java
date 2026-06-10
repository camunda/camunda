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
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.entities.WaitStateJobDetails;
import io.camunda.search.entities.WaitStateMessageDetails;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(ElementInstanceController.class)
public class ElementInstanceControllerTest extends RestControllerTest {

  static final String ELEMENTS_BASE_URL = "/v2/element-instances";
  static final String WAIT_STATES_URL = ELEMENTS_BASE_URL + "/wait-states/search";

  private static final String EXPECTED_WAIT_STATE_RESPONSE =
      """
      {
        "items": [
          {
            "rootProcessInstanceKey": "2251799813685249",
            "processInstanceKey": "2251799813685249",
            "elementInstanceKey": "2251799813685251",
            "elementId": "payment-task",
            "elementType": "SERVICE_TASK",
            "details": {
              "waitStateType": "JOB",
              "jobKey": "2251799813685252",
              "jobType": "payment-service",
              "jobKind": "EXECUTION_LISTENER",
              "listenerEventType": "START"
            }
          },
          {
            "rootProcessInstanceKey": "2251799813685249",
            "processInstanceKey": "2251799813685249",
            "elementInstanceKey": "2251799813685253",
            "elementId": "order-received",
            "elementType": "INTERMEDIATE_CATCH_EVENT",
            "details": {
              "waitStateType": "MESSAGE",
              "messageName": "order-confirmed",
              "correlationKey": "order-42"
            }
          },
          {
            "rootProcessInstanceKey": "2251799813685249",
            "processInstanceKey": "2251799813685249",
            "elementInstanceKey": "2251799813685261",
            "elementId": "notify-task",
            "elementType": "SERVICE_TASK",
            "details": {
              "waitStateType": "JOB",
              "jobKey": "2251799813685260",
              "jobType": "notification-service",
              "jobKind": "BPMN_ELEMENT"
            }
          }
        ]
      }
      """;

  private static final SearchQueryResult<WaitStateEntity> DUMMY_WAIT_STATE_ITEMS =
      SearchQueryResult.of(
          new WaitStateEntity.Builder()
              .processInstanceKey(2251799813685249L)
              .elementInstanceKey(2251799813685251L)
              .elementId("payment-task")
              .elementType(FlowNodeType.SERVICE_TASK)
              .rootProcessInstanceKey(2251799813685249L)
              .tenantId("<default>")
              .details(
                  new WaitStateJobDetails.Builder()
                      .jobKey(2251799813685252L)
                      .jobType("payment-service")
                      .jobKind(JobKind.EXECUTION_LISTENER)
                      .listenerEventType(ListenerEventType.START)
                      .build())
              .build(),
          new WaitStateEntity.Builder()
              .processInstanceKey(2251799813685249L)
              .elementInstanceKey(2251799813685253L)
              .elementId("order-received")
              .elementType(FlowNodeType.INTERMEDIATE_CATCH_EVENT)
              .rootProcessInstanceKey(2251799813685249L)
              .tenantId("<default>")
              .details(
                  new WaitStateMessageDetails.Builder()
                      .messageName("order-confirmed")
                      .correlationKey("order-42")
                      .build())
              .build(),
          new WaitStateEntity.Builder()
              .processInstanceKey(2251799813685249L)
              .elementInstanceKey(2251799813685261L)
              .elementId("notify-task")
              .elementType(FlowNodeType.SERVICE_TASK)
              .rootProcessInstanceKey(2251799813685249L)
              .tenantId("<default>")
              .details(
                  new WaitStateJobDetails.Builder()
                      .jobKey(2251799813685260L)
                      .jobType("notification-service")
                      .jobKind(JobKind.BPMN_ELEMENT)
                      .build())
              .build());

  @MockitoBean ElementInstanceServices elementInstanceServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;
  @Captor ArgumentCaptor<SetVariablesRequest> requestCaptor;

  @BeforeEach
  void setup() {
    Mockito.lenient()
        .when(serviceRegistry.elementInstanceServices(any()))
        .thenReturn(elementInstanceServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSetSetVariables() {
    // given
    when(elementInstanceServices.setVariables(any(SetVariablesRequest.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new VariableDocumentRecord()));

    final var request =
        """
            {
              "variables": {
                "key": "value"
              },
              "local": true,
              "operationReference": 123
            }""";

    // when/then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(elementInstanceServices).setVariables(requestCaptor.capture(), any());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.elementInstanceKey()).isEqualTo(123L);
    assertThat(capturedRequest.variables()).isEqualTo(Map.of("key", "value"));
    assertThat(capturedRequest.local()).isTrue();
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldRejectSetVariablesWithSetVariablesNull() {
    // given
    final var request =
        """
            {
                "variables": null
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No variables provided.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
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
  void shouldRejectSetVariablesWithSetVariablesEmpty() {
    // given
    final var request =
        """
            {
                "variables": {}
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No variables provided.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
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
  void shouldRejectSetSetVariablesWithOperationReferenceNotValid() {
    // given
    final var request =
        """
            {
              "variables": {
                "key": "value"
              },
              "operationReference": -123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"The value for operationReference is '-123' but must be > 0.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
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
  void shouldReturnGatewayTimeoutWhenSetVariablesTimesOut() {
    // given
    when(elementInstanceServices.setVariables(any(SetVariablesRequest.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Task listener blocked set variables"))));

    final var request =
        """
            {
              "variables": {
                "key": "value"
              }
            }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("DEADLINE_EXCEEDED")
        .jsonPath("$.detail")
        .isEqualTo("Expected to handle request, but request timed out between gateway and broker")
        .jsonPath("$.status")
        .isEqualTo(504);
  }

  @Test
  void shouldSearchWaitStatesWithEmptyBody() {
    // given
    when(elementInstanceServices.searchWaitStates(any(), any())).thenReturn(DUMMY_WAIT_STATE_ITEMS);

    // when / then
    webClient
        .post()
        .uri(WAIT_STATES_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_WAIT_STATE_RESPONSE, JsonCompareMode.LENIENT);
  }

  @Test
  void shouldSearchWaitStatesWithFilterByProcessInstanceKey() {
    // given
    when(elementInstanceServices.searchWaitStates(any(), any())).thenReturn(DUMMY_WAIT_STATE_ITEMS);

    final var request =
        """
        {
          "filter": {
            "processInstanceKey": "2251799813685249"
          }
        }
        """;

    // when / then
    webClient
        .post()
        .uri(WAIT_STATES_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_WAIT_STATE_RESPONSE, JsonCompareMode.LENIENT);
  }
}
