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

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = ProcessInstanceController.class)
public class ProcessInstanceControllerTest extends RestControllerTest {

  static final String EXPECTED_START_RESPONSE =
      """
      {
         "processKey":123,
         "bpmnProcessId":"bpmnProcessId",
         "version":-1,
         "processInstanceKey":123,
         "tenantId":"tenantId"
      }""";
  static final String PROCESS_INSTANCES_START_URL = "/v2/process-instances";

  @Captor ArgumentCaptor<ProcessInstanceCreateRequest> requestCaptor;

  @MockBean ProcessInstanceServices processInstanceServices;

  @BeforeEach
  void setupServices() {
    when(processInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(processInstanceServices);
  }

  @Test
  void shouldCreateProcessInstancesWithProcessDefinitionKey() {
    // given
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "processDefinitionKey": 123
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstance(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
  }

  @Test
  void shouldCreateProcessInstancesWithBpmnProcessIdAndVersion() {
    // given
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId",
            "version": 1
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstance(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(1);
  }

  @Test
  void shouldCreateProcessInstancesWithBpmnProcessIdWithoutVersion() {
    // given
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId"
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstance(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(-1);
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithProcessDefinitionKey() {
    // given
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstanceWithResult(
            any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "processDefinitionKey": 123,
            "awaitCompletion": true
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstanceWithResult(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.awaitCompletion()).isTrue();
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithBpmnProcessIdAndVersion() {
    // given
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstanceWithResult(
            any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId",
            "version": 1,
            "awaitCompletion": true
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstanceWithResult(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(1);
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithBpmnProcessIdWithoutVersion() {
    // given
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(processInstanceServices.createProcessInstanceWithResult(
            any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId",
            "awaitCompletion": true
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
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstanceWithResult(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(-1);
  }

  @Test
  void shouldRejectCreateProcessInstancesIfNoBpmnProcessIdOrProcessDefinitionKeyAreProvided() {
    // given
    final var request =
        """
        {
            "version": 1,
            "awaitCompletion": true
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"At least one of [bpmnProcessId, processDefinitionKey] is required.",
            "instance":"/v2/process-instances"
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
        .json(expectedBody);
  }

  @Test
  void shouldRejectCreateProcessInstancesIfBothBpmnProcessIdOrProcessDefinitionKeyAreProvided() {
    // given
    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId",
            "processDefinitionKey": 123,
            "version": 1
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"Only one of [bpmnProcessId, processDefinitionKey] is allowed.",
            "instance":"/v2/process-instances"
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
        .json(expectedBody);
  }
}
