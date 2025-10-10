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

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
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
             "tenantId":"tenantId",
             "variables":{},
             "tags":[]
          }""";
  static final String PROCESS_INSTANCES_START_URL = "/v2/process-instances";
  static final String CANCEL_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/cancellation";
  static final String MIGRATE_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/migration";
  static final String MODIFY_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/modification";

  @Captor ArgumentCaptor<ProcessInstanceCreateRequest> createRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceCancelRequest> cancelRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceMigrateRequest> migrateRequestCaptor;
  @Captor ArgumentCaptor<ProcessInstanceModifyRequest> modifyRequestCaptor;
  @MockitoBean ProcessInstanceServices processInstanceServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(processInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(processInstanceServices);
  }

  @Test
  void shouldCreateProcessInstancesWithProcessDefinitionKey() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
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
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

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
    final var expectedResponse =
        """
            {
              "processDefinitionKey":"123",
              "processDefinitionId":"bpmnProcessId",
              "processDefinitionVersion":-1,
              "processInstanceKey":"123",
              "tenantId":"<default>",
              "variables":{},
              "tags":[]
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.tenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldCreateProcessInstancesWithBpmnProcessIdAndVersion() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
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
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(1);
  }

  @Test
  void shouldCreateProcessInstancesWithBpmnProcessIdWithoutVersion() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
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
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

    verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(-1);
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithProcessDefinitionKey() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(processInstanceServices.createProcessInstanceWithResult(
            any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
            {
                "processDefinitionKey": "123",
                "awaitCompletion": true,
            "tenantId": "tenantId"
        }""";

    // when / then
    final ResponseSpec response =
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.awaitCompletion()).isTrue();
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithBpmnProcessIdAndVersion() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
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
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.bpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(capturedRequest.version()).isEqualTo(1);
  }

  @Test
  void shouldCreateProcessInstancesWithResultWithBpmnProcessIdWithoutVersion() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(123L)
            .setTenantId("tenantId");

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
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
        webClient
            .post()
            .uri(PROCESS_INSTANCES_START_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_START_RESPONSE, JsonCompareMode.STRICT);

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
                "processDefinitionVersion": 1,
                "awaitCompletion": true
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"At least one of [processDefinitionId, processDefinitionKey] is required",
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreateProcessInstancesIfBothBpmnProcessIdOrProcessDefinitionKeyAreProvided() {
    // given
    final var request =
        """
            {
                "processDefinitionId": "bpmnProcessId",
                "processDefinitionKey": "123",
                "processDefinitionVersion": 1
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Only one of [processDefinitionId, processDefinitionKey] is allowed",
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldMigrateProcessInstance() {
    // given
    when(processInstanceServices.migrateProcessInstance(any(ProcessInstanceMigrateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceMigrationRecord()));

    final var request =
        """
            {
              "targetProcessDefinitionKey": "123456",
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsNull() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": "123456",
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsEmpty() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": "123456",
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectMigrateProcessInstanceWithMappingInstructionsNotValid() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": "123456",
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectMigrateProcessInstanceWithOperationReferenceNotValid() {
    // given
    final var request =
        """
            {
              "targetProcessDefinitionKey": "123456",
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
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
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                }
              ],
              "terminateInstructions": [
                {},
                {
                  "elementInstanceKey": "654321"
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
                  "ancestorElementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": "123456"
                },
                {
                  "elementInstanceKey": "654321"
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldCancelProcessInstanceBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    when(processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
            any(ProcessInstanceFilter.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "filter":
               {
                  "processDefinitionId": "test-process-definition-id"
                }
            }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/cancellation")
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
          {"batchOperationKey":"123","batchOperationType":"CANCEL_PROCESS_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(processInstanceServices)
        .cancelProcessInstanceBatchOperationWithResult(any(ProcessInstanceFilter.class));
  }

  @Test
  void shouldModifyProcessInstanceBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.MODIFY_PROCESS_INSTANCE);

    when(processInstanceServices.modifyProcessInstancesBatchOperation(
            any(ProcessInstanceModifyBatchOperationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "filter": {
                "processDefinitionId": "test-process-definition-id"
              },
              "moveInstructions": [
                {
                  "sourceElementId": "source1",
                  "targetElementId": "target1"
                }
              ]
            }
            """;

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/modification")
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
          {"batchOperationKey":"123","batchOperationType":"MODIFY_PROCESS_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(processInstanceServices)
        .modifyProcessInstancesBatchOperation(
            any(ProcessInstanceModifyBatchOperationRequest.class));
  }

  @Test
  public void shouldGetElementStatistics() {
    // given
    final long processInstanceKey = 1L;
    final var stats = List.of(new ProcessFlowNodeStatisticsEntity("node1", 1L, 1L, 1L, 1L));
    when(processInstanceServices.elementStatistics(processInstanceKey)).thenReturn(stats);
    final var response =
        """
            {"items":[
              {
                "elementId": "node1",
                "active": 1,
                "canceled": 1,
                "incidents": 1,
                "completed": 1
              }
            ]}""";

    // when / then
    webClient
        .get()
        .uri(
            "%s/%d/statistics/element-instances"
                .formatted(PROCESS_INSTANCES_START_URL, processInstanceKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processInstanceServices).elementStatistics(processInstanceKey);
  }

  @Test
  void shouldResolveIncidentsBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT);

    when(processInstanceServices.resolveIncidentsBatchOperationWithResult(
            any(ProcessInstanceFilter.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "filter":
               {
                  "processDefinitionId": "test-process-definition-id"
                }
            }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/incident-resolution")
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
          {"batchOperationKey":"123","batchOperationType":"RESOLVE_INCIDENT"}
        """,
            JsonCompareMode.STRICT);

    verify(processInstanceServices)
        .resolveIncidentsBatchOperationWithResult(any(ProcessInstanceFilter.class));
  }

  @Test
  void shouldMigrateProcessInstancesBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    when(processInstanceServices.migrateProcessInstancesBatchOperation(
            any(ProcessInstanceMigrateBatchOperationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
           {
            "filter": {
              "processDefinitionId": "test-process-definition-id"
            },
            "migrationPlan": {
                "targetProcessDefinitionKey": "123",
                "mappingInstructions": [
                  {
                    "sourceElementId": "a",
                    "targetElementId": "b"
                  }
                ]
              }
           }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/migration")
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
          {"batchOperationKey":"123","batchOperationType":"MIGRATE_PROCESS_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(processInstanceServices)
        .migrateProcessInstancesBatchOperation(
            any(ProcessInstanceMigrateBatchOperationRequest.class));
  }

  @Test
  public void shouldGetSequenceFlows() {
    // given
    final long processInstanceKey = 1L;
    final var sequenceFlows =
        List.of(new SequenceFlowEntity("pi1_sequenceFlow1", "node1", 1L, 1L, "pd1", "<default>"));
    when(processInstanceServices.sequenceFlows(processInstanceKey)).thenReturn(sequenceFlows);
    final var response =
        """
            {"items":[
              {
                "sequenceFlowId": "pi1_sequenceFlow1",
                "processInstanceKey": "1",
                "processDefinitionKey": "1",
                "processDefinitionId": "pd1",
                "elementId": "node1",
                "tenantId": "<default>"
              }
            ]}""";

    // when / then
    webClient
        .get()
        .uri("%s/%d/sequence-flows".formatted(PROCESS_INSTANCES_START_URL, processInstanceKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processInstanceServices).sequenceFlows(processInstanceKey);
  }

  @Test
  public void shouldGetProcessInstanceIncidents() {
    // given
    final long processInstanceKey = 1L;
    final var queryResult =
        new SearchQueryResult.Builder<IncidentEntity>()
            .total(1)
            .items(
                List.of(
                    new IncidentEntity(
                        2251799814751259L,
                        2251799814751221L,
                        "def_id",
                        2251799814751255L,
                        ErrorType.FORM_NOT_FOUND,
                        "Form not found",
                        "Activity_07rrek1",
                        2251799814751258L,
                        OffsetDateTime.parse("2025-05-23T17:41:24.406Z"),
                        IncidentState.ACTIVE,
                        1L,
                        "<default>")))
            .startCursor("<cursor before>")
            .endCursor("<cursor after>")
            .build();
    final var query = new IncidentQuery.Builder().build();
    when(processInstanceServices.searchIncidents(processInstanceKey, query))
        .thenReturn(queryResult);
    final var expectedResponse =
        """
    {
        "items": [
            {
                "processDefinitionId": "def_id",
                "errorType": "FORM_NOT_FOUND",
                "errorMessage": "Form not found",
                "elementId": "Activity_07rrek1",
                "creationTime": "2025-05-23T17:41:24.406Z",
                "state": "ACTIVE",
                "tenantId": "<default>",
                "incidentKey": "2251799814751259",
                "processDefinitionKey": "2251799814751221",
                "processInstanceKey": "2251799814751255",
                "elementInstanceKey": "2251799814751258",
                "jobKey": "1"
            }
        ],
        "page": {
            "totalItems": 1,
            "startCursor": "<cursor before>",
            "endCursor": "<cursor after>",
            "hasMoreTotalItems": false
        }
    }""";

    // when / then
    webClient
        .post()
        .uri("%s/%d/incidents/search".formatted(PROCESS_INSTANCES_START_URL, processInstanceKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(processInstanceServices).searchIncidents(processInstanceKey, query);
  }

  @Test
  void shouldRejectCancelProcessInstanceBatchOperationWithNoRequestBody() {
    // given
    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Required request body is missing",
                "instance":"/v2/process-instances/cancellation"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/cancellation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCancelProcessInstanceBatchOperationWithEmptyRequestBody() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/process-instances/cancellation"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/cancellation")
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
  void shouldRejectCancelProcessInstanceBatchOperationWithEmptyFilter() {
    // given
    final var request =
        """
        {
          "filter": {}
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of filter criteria is required.",
                "instance":"/v2/process-instances/cancellation"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/cancellation")
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
  void shouldRejectResolveIncidentsBatchOperationWithNoRequestBody() {
    // given
    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Required request body is missing",
                "instance":"/v2/process-instances/incident-resolution"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/incident-resolution")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectResolveIncidentsBatchOperationWithEmptyRequestBody() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/process-instances/incident-resolution"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/incident-resolution")
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
  void shouldRejectResolveIncidentsBatchOperationWithEmptyFilter() {
    // given
    final var request =
        """
        {
          "filter": {}
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of filter criteria is required.",
                "instance":"/v2/process-instances/incident-resolution"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/incident-resolution")
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
  void shouldRejectMigrateProcessInstancesBatchOperationWithNoFilter() {
    // given
    final var request =
        """
        {
          "migrationPlan": {
            "targetProcessDefinitionKey": "123",
            "mappingInstructions": [
              {
                "sourceElementId": "a",
                "targetElementId": "b"
              }
            ]
          }
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/process-instances/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/migration")
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
  void shouldRejectMigrateProcessInstancesBatchOperationWithEmptyFilter() {
    // given
    final var request =
        """
        {
          "filter": {},
          "migrationPlan": {
            "targetProcessDefinitionKey": "123",
            "mappingInstructions": [
              {
                "sourceElementId": "a",
                "targetElementId": "b"
              }
            ]
          }
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of filter criteria is required.",
                "instance":"/v2/process-instances/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/migration")
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
  void shouldRejectMigrateProcessInstancesBatchOperationWithNoMigrationPlan() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    when(processInstanceServices.migrateProcessInstancesBatchOperation(
            any(ProcessInstanceMigrateBatchOperationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
           {
            "filter": {
              "processDefinitionId": "test-process-definition-id"
            }
           }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No migrationPlan provided.",
                "instance":"/v2/process-instances/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/migration")
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
  void shouldRejectMigrateProcessInstancesBatchOperationWithEmptyMigrationPlan() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    when(processInstanceServices.migrateProcessInstancesBatchOperation(
            any(ProcessInstanceMigrateBatchOperationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
           {
            "filter": {
              "processDefinitionId": "test-process-definition-id"
            },
            "migrationPlan": {}
           }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No targetProcessDefinitionKey provided. No mappingInstructions provided.",
                "instance":"/v2/process-instances/migration"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/migration")
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
  void shouldRejectModifyProcessInstancesBatchOperationWithNoFilter() {
    // given
    final var request =
        """
        {
          "moveInstructions": [
            {
              "sourceElementId": "source1",
              "targetElementId": "target1"
            }
          ]
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/process-instances/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/modification")
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
  void shouldRejectModifyProcessInstancesBatchOperationWithEmptyFilter() {
    // given
    final var request =
        """
        {
          "filter": {},
          "moveInstructions": [
            {
              "sourceElementId": "source1",
              "targetElementId": "target1"
            }
          ]
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"At least one of filter criteria is required.",
                "instance":"/v2/process-instances/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/modification")
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
  void shouldRejectModifyProcessInstancesBatchOperationWithNoMoveInstructions() {
    // given
    final var request =
        """
        {
          "filter": {
              "processDefinitionId": "test-process-definition-id"
            }
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No moveInstructions provided.",
                "instance":"/v2/process-instances/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/modification")
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
  void shouldRejectModifyProcessInstancesBatchOperationWithEmptyMoveInstructions() {
    // given
    final var request =
        """
        {
          "filter": {
              "processDefinitionId": "test-process-definition-id"
            },
          "moveInstructions": []
        }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No moveInstructions provided.",
                "instance":"/v2/process-instances/modification"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/modification")
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
