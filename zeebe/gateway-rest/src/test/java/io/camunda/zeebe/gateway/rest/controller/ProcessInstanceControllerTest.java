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

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.auth.Authentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(value = ProcessInstanceController.class)
public class ProcessInstanceControllerTest extends RestControllerTest {

  static final String EXPECTED_START_RESPONSE =
      """
          {
             "processDefinitionKey":"123",
             "processDefinitionId":"bpmnProcessId",
             "processDefinitionVersion":-1,
             "processInstanceKey":"123",
             "tenantId":"tenantId"
          }""";
  static final String PROCESS_INSTANCES_START_URL = "/v2/process-instances";
  static final String CANCEL_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/cancellation";
  static final String MIGRATE_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/migration";
  static final String MODIFY_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/modification";

  @Captor ArgumentCaptor<ProcessInstanceCreateRequest> createRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceCancelRequest> cancelRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceMigrateRequest> migrateRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceModifyRequest> modifyRequestCaptor;
  @MockBean ProcessInstanceServices processInstanceServices;
  @MockBean MultiTenancyConfiguration multiTenancyCfg;

  @BeforeEach
  void setupServices() {
    when(processInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(processInstanceServices);
  }

  @Test
  void shouldCreateProcessInstancesWithProcessDefinitionKey() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
            "processDefinitionKey": "123",
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE);

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.tenantId()).isEqualTo("tenantId");
  }

  @Test
  void shouldCreateProcessInstancesWithoutTenantId() {
    // given
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("<default>");

    when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
            {
                "processDefinitionKey": "123"
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
        .json(
            """
{
   "processDefinitionKey":"123",
   "processDefinitionId":"bpmnProcessId",
   "processDefinitionVersion":-1,
   "processInstanceKey":"123",
   "tenantId":"<default>"
}""");

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.tenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldCreateProcessInstancesWithBpmnProcessIdAndVersion() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
                "processDefinitionId": "bpmnProcessId",
                "processDefinitionVersion": 1,
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
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
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
                "processDefinitionId": "bpmnProcessId",
            "tenantId": "tenantId"
            }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
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
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
                "awaitCompletion": true,
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
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
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
                "processDefinitionId": "bpmnProcessId",
                "processDefinitionVersion": 1,
                "awaitCompletion": true,
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
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
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
                "processDefinitionId": "bpmnProcessId",
                "awaitCompletion": true,
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(PROCESS_INSTANCES_START_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk());

    response
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
                "detail":"At least one of [processDefinitionId, processDefinitionKey] is required.",
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
                "processDefinitionId": "bpmnProcessId",
                "processDefinitionKey": 123,
                "version": 1
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"Only one of [processDefinitionId, processDefinitionKey] is allowed.",
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
                "processDefinitionId": "bpmnProcessId",
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

  @Test
  void shouldMigrateProcessInstance() {
    // given
    when(processInstanceServices.migrateProcessInstance(any(ProcessInstanceMigrateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceMigrationRecord()));

    final var request =
        """
            {
              "targetProcessDefinitionKey": 123456,
              "mappingInstructions": [
                {
                  "sourceElementId": "sourceElementId1",
                  "targetElementId": "targetElementId1"
                },
                {
                  "sourceElementId": "sourceElementId2",
                  "targetElementId": "targetElementId2"
                }
              ],
              "operationReference": 123
            }""";

    // when/then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).migrateProcessInstance(migrateRequestCaptor.capture());
    final var capturedRequest = migrateRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.targetProcessDefinitionKey()).isEqualTo(123456);
    assertThat(capturedRequest.mappingInstructions()).isNotEmpty();
    assertThat(capturedRequest.mappingInstructions().size()).isEqualTo(2);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldRejectMigrateProcessInstanceWithTargetProcessDefinitionKey() {
    // given
    final var request =
        """
            {
              "mappingInstructions": [
                {
                  "sourceElementId": "sourceElementId1",
                  "targetElementId": "targetElementId1"
                },
                {
                  "sourceElementId": "sourceElementId2",
                  "targetElementId": "targetElementId2"
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No targetProcessDefinitionKey provided.",
                "instance":"/v2/process-instances/1/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
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
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsNull() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": 123456,
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No mappingInstructions provided.",
                "instance":"/v2/process-instances/1/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
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
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsEmpty() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": 123456,
              "mappingInstructions": [],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No mappingInstructions provided.",
                "instance":"/v2/process-instances/1/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
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
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsNotValid() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": 123456,
              "mappingInstructions": [
                {
                  "sourceElementId": "sourceElementId1",
                  "targetElementId": "targetElementId1"
                },
                {
                  "sourceElementId": "sourceElementId2"
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"All [sourceElementId, targetElementId] are required.",
                "instance":"/v2/process-instances/1/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
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
  void shouldRejectMigrateProcessInstanceWithOperationReferenceNotValid() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": 123456,
              "mappingInstructions": [
                {
                  "sourceElementId": "sourceElementId1",
                  "targetElementId": "targetElementId1"
                },
                {
                  "sourceElementId": "sourceElementId2",
                  "targetElementId": "targetElementId2"
                }
              ],
              "operationReference": -123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"The value for operationReference is '-123' but must be > 0.",
                "instance":"/v2/process-instances/1/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri(MIGRATE_PROCESS_URL.formatted("1"))
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
  void shouldModifyProcessInstance() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ],
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    // when/then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).modifyProcessInstance(modifyRequestCaptor.capture());
    final var capturedRequest = modifyRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.activateInstructions()).isNotEmpty();
    assertThat(capturedRequest.activateInstructions().size()).isEqualTo(2);
    assertThat(capturedRequest.terminateInstructions()).isNotEmpty();
    assertThat(capturedRequest.terminateInstructions().size()).isEqualTo(2);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldModifyProcessInstanceWithoutActivateInstructions() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

    final var request =
        """
            {
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    // when/then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).modifyProcessInstance(modifyRequestCaptor.capture());
    final var capturedRequest = modifyRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.activateInstructions()).isEmpty();
    assertThat(capturedRequest.terminateInstructions()).hasSize(2);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldModifyProcessInstanceWithoutTerminateInstructions() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ],
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    // when/then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).modifyProcessInstance(modifyRequestCaptor.capture());
    final var capturedRequest = modifyRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.activateInstructions()).hasSize(2);
    assertThat(capturedRequest.terminateInstructions()).isEmpty();
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldModifyProcessInstanceWithActivateInstructionsNoAncestorKey() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId"
                },
                {
                  "elementId": "elementId2"
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    // when/then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(processInstanceServices).modifyProcessInstance(modifyRequestCaptor.capture());
    final var capturedRequest = modifyRequestCaptor.getValue();
    assertThat(capturedRequest.processInstanceKey()).isEqualTo(1);
    assertThat(capturedRequest.activateInstructions()).hasSize(2);
    assertThat(capturedRequest.terminateInstructions()).hasSize(2);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldRejectModifyProcessInstanceWithActivateInstructionsElementNull() {
    // given
    final var request =
        """
            {
              "activateInstructions": [
                {
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No elementId provided.",
                "instance":"/v2/process-instances/1/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
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
  void shouldRejectModifyProcessInstanceWithVariableInstructionsElementNull() {
    // given
    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "variableInstructions": [
                    {
                      "scopeId": "scopeId"
                    }
                  ],
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No variables provided.",
                "instance":"/v2/process-instances/1/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
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
  void shouldRejectModifyProcessInstanceWithTerminateInstructionsElementNull() {
    // given
    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "terminateInstructions": [
                {},
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No elementInstanceKey provided.",
                "instance":"/v2/process-instances/1/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
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
  void shouldRejectModifyProcessInstanceWithOperationReferenceNotValid() {
    // given
    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ],
                  "ancestorElementInstanceKey": 123456
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": 654321
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": 123456
                },
                {
                  "elementInstanceKey": 654321
                }
              ],
              "operationReference": -123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"The value for operationReference is '-123' but must be > 0.",
                "instance":"/v2/process-instances/1/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri(MODIFY_PROCESS_URL.formatted("1"))
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
  void shouldCancelProcessInstanceBatchOperation() throws Exception {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION);

    when(processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
            any(ProcessInstanceFilter.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "processDefinitionId": "test-process-definition-id"
            }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/batch-operations/cancellation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
          {"batchOperationKey":"123","batchOperationType":"PROCESS_CANCELLATION"}
        """);

    verify(processInstanceServices)
        .cancelProcessInstanceBatchOperationWithResult(any(ProcessInstanceFilter.class));
  }
}
