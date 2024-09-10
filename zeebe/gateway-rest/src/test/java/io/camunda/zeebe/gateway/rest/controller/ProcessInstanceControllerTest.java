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
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
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
  static final String CANCEL_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/cancellation";

  @Captor ArgumentCaptor<ProcessInstanceCreateRequest> createRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceCancelRequest> cancelRequestCaptor;
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

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
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

  @Test
  void shouldRejectCreateProcessInstancesIfOperationReferenceIsNotValid() {
    // given
    final var request =
        """
        {
            "bpmnProcessId": "bpmnProcessId",
            "operationReference": -1
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"The value for operationReference is '-1' but must be > 0.",
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
  void shouldCancelProcessInstance() {
    // given
    when(processInstanceServices.cancelProcessInstance(any(ProcessInstanceCancelRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceRecord()));

    final var request =
        """
        {
          "operationReference": 123
        }""";

    // when/then
    webClient
        .post()
        .uri(CANCEL_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).cancelProcessInstance(cancelRequestCaptor.capture());
    final var capturedRequest = cancelRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldCancelProcessInstanceWithNoBody() {
    // given
    when(processInstanceServices.cancelProcessInstance(any(ProcessInstanceCancelRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceRecord()));

    // when/then
    webClient
        .post()
        .uri(CANCEL_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).cancelProcessInstance(cancelRequestCaptor.capture());
    final var capturedRequest = cancelRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.operationReference()).isNull();
  }

  @Test
  void shouldDeleteResourceWithEmptyBody() {
    // given
    when(processInstanceServices.cancelProcessInstance(any(ProcessInstanceCancelRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceRecord()));

    final var request =
        """
        {}""";

    // when/then
    webClient
        .post()
        .uri(CANCEL_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).cancelProcessInstance(cancelRequestCaptor.capture());
    final var capturedRequest = cancelRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.operationReference()).isNull();
  }

  @Test
  void shouldRejectCancelProcessInstanceWithOperationReferenceNotValid() {
    // given
    final var request =
        """
        {
          "operationReference": -123
        }""";

    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"The value for operationReference is '-123' but must be > 0.",
            "instance":"/v2/process-instances/1/cancellation"
         }""";

    // when / then
    webClient
        .post()
        .uri(CANCEL_PROCESS_URL.formatted("1"))
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
