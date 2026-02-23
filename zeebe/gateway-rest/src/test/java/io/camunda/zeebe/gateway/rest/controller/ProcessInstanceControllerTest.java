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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
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
             "tags":[],
             "businessId": null
          }""";
  static final String PROCESS_INSTANCES_START_URL = "/v2/process-instances";
  static final String CANCEL_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/cancellation";
  static final String DELETE_PROCESS_URL = PROCESS_INSTANCES_START_URL + "/%s/deletion";
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
              "tags":[],
              "businessId": null
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
  void shouldCreateProcessInstancesWithBusinessId() {
    // given
    final var businessId = "order-12345";
    final var mockResponse =
        new ProcessInstanceCreationRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(456L)
            .setTenantId("<default>")
            .setBusinessId(businessId);

    when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
            {
                "processDefinitionKey": "123",
                "businessId": "order-12345"
            }""";

    final var expectedResponse =
        """
            {
              "processDefinitionKey":"123",
              "processDefinitionId":"bpmnProcessId",
              "processDefinitionVersion":-1,
              "processInstanceKey":"456",
              "tenantId":"<default>",
              "variables":{},
              "tags":[],
              "businessId":"order-12345"
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
    assertThat(capturedRequest.businessId()).isEqualTo(businessId);
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
  void shouldCreateProcessInstancesWithResultAndCustomRequestTimeout() {
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
                "requestTimeout": 600000,
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
    assertThat(capturedRequest.requestTimeout()).isEqualTo(600000L);
  }

  @Test
  void shouldCreateProcessInstancesWithResultAndBusinessId() {
    // given
    final var businessId = "order-12345";
    final var mockResponse =
        new ProcessInstanceResultRecord()
            .setProcessDefinitionKey(123L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(456L)
            .setTenantId("<default>")
            .setBusinessId(businessId);

    when(processInstanceServices.createProcessInstanceWithResult(
            any(ProcessInstanceCreateRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));

    final var request =
        """
            {
                "processDefinitionKey": "123",
                "awaitCompletion": true,
                "businessId": "order-12345"
            }""";

    final var expectedResponse =
        """
            {
              "processDefinitionKey":"123",
              "processDefinitionId":"bpmnProcessId",
              "processDefinitionVersion":-1,
              "processInstanceKey":"456",
              "tenantId":"<default>",
              "variables":{},
              "tags":[],
              "businessId":"order-12345"
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

    verify(processInstanceServices).createProcessInstanceWithResult(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
    assertThat(capturedRequest.awaitCompletion()).isTrue();
    assertThat(capturedRequest.businessId()).isEqualTo(businessId);
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

    final var variables = new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("foo", "bar")));
    final var expectedMappedRequest =
        new ProcessInstanceModifyRequest(
            1L,
            List.of(
                new ProcessInstanceModificationActivateInstruction()
                    .setElementId("elementId")
                    .setAncestorScopeKey(123456L)
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)
                            .setElementId("variableScopeId"))
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)),
                new ProcessInstanceModificationActivateInstruction()
                    .setElementId("elementId2")
                    .setAncestorScopeKey(654321L),
                new ProcessInstanceModificationActivateInstruction()
                    .setElementId("elementId3")
                    .setAncestorScopeKey(-1L)
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)
                            .setElementId("variableScopeId"))
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)),
                new ProcessInstanceModificationActivateInstruction()
                    .setElementId("elementId4")
                    .setAncestorScopeKey(-1L)),
            List.of(
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId")
                    .setTargetElementId("targetElementId")
                    .setAncestorScopeKey(123456L)
                    .setInferAncestorScopeFromSourceHierarchy(false)
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)
                            .setElementId("variableScopeId"))
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId2")
                    .setTargetElementId("targetElementId2")
                    .setAncestorScopeKey(654321L)
                    .setInferAncestorScopeFromSourceHierarchy(false),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId3")
                    .setTargetElementId("targetElementId3")
                    .setAncestorScopeKey(-1L)
                    .setInferAncestorScopeFromSourceHierarchy(false),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId4")
                    .setTargetElementId("targetElementId4")
                    .setAncestorScopeKey(-1L)
                    .setInferAncestorScopeFromSourceHierarchy(true),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId4b")
                    .setTargetElementId("targetElementId4b")
                    .setAncestorScopeKey(-1L)
                    .setUseSourceParentKeyAsAncestorScopeKey(true),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId5")
                    .setTargetElementId("targetElementId5")
                    .setAncestorScopeKey(-1L)
                    .setInferAncestorScopeFromSourceHierarchy(false),
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("sourceElementId6")
                    .setTargetElementId("targetElementId6")
                    .setAncestorScopeKey(-1L)
                    .setInferAncestorScopeFromSourceHierarchy(false)
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables)
                            .setElementId("variableScopeId"))
                    .addVariableInstruction(
                        new ProcessInstanceModificationVariableInstruction()
                            .setVariables(variables))),
            List.of(
                new ProcessInstanceModificationTerminateInstruction()
                    .setElementInstanceKey(123456L),
                new ProcessInstanceModificationTerminateInstruction().setElementId("elementId")),
            123L);

    final var request =
        """
            {
              "activateInstructions": [
                {
                  "elementId": "elementId",
                  "ancestorElementInstanceKey": "123456",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      },
                      "scopeId": "variableScopeId"
                    },
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ]
                },
                {
                  "elementId": "elementId2",
                  "ancestorElementInstanceKey": "654321"
                },
                {
                  "elementId": "elementId3",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      },
                      "scopeId": "variableScopeId"
                    },
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ]
                },
                {
                  "elementId": "elementId4"
                }
              ],
              "moveInstructions": [
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId"
                  },
                  "targetElementId": "targetElementId",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "direct",
                    "ancestorElementInstanceKey": "123456"
                  },
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      },
                      "scopeId": "variableScopeId"
                    },
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ]
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId2"
                  },
                  "targetElementId": "targetElementId2",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "direct",
                    "ancestorElementInstanceKey": "654321"
                  }
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId3"
                  },
                  "targetElementId": "targetElementId3",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "direct"
                  }
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId4"
                  },
                  "targetElementId": "targetElementId4",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "inferred"
                  }
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId4b"
                  },
                  "targetElementId": "targetElementId4b",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "sourceParent"
                  }
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId5"
                  },
                  "targetElementId": "targetElementId5"
                },
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "sourceElementId6"
                  },
                  "targetElementId": "targetElementId6",
                  "variableInstructions": [
                    {
                      "variables": {
                        "foo": "bar"
                      },
                      "scopeId": "variableScopeId"
                    },
                    {
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ]
                }
              ],
              "terminateInstructions": [
                {
                  "elementInstanceKey": "123456"
                },
                {
                  "elementId": "elementId"
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
    assertThat(modifyRequestCaptor.getValue()).isEqualTo(expectedMappedRequest);
  }

  @Test
  void shouldModifyProcessInstanceWithOnlyTerminateInstructions() {
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
                  "elementId": "elementId"
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
    assertThat(capturedRequest.moveInstructions()).isEmpty();
    assertThat(capturedRequest.terminateInstructions()).hasSize(2);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldModifyProcessInstanceWithOnlyActivateInstructions() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

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
    assertThat(capturedRequest.moveInstructions()).isEmpty();
    assertThat(capturedRequest.terminateInstructions()).isEmpty();
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldModifyProcessInstanceWithOnlyMoveInstructions() {
    // given
    when(processInstanceServices.modifyProcessInstance(any(ProcessInstanceModifyRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ProcessInstanceModificationRecord()));

    final var request =
        """
            {
              "moveInstructions": [
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "elementId"
                  },
                  "targetElementId": "elementId2"
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
    assertThat(capturedRequest.moveInstructions()).hasSize(1);
    assertThat(capturedRequest.terminateInstructions()).isEmpty();
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
                  ]
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
  void shouldRejectModifyProcessInstanceWithVariableInstructionsElementNullOnMove() {
    // given
    final var request =
        """
            {
              "moveInstructions": [
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "elementId"
                  },
                  "targetElementId": "elementId2",
                  "variableInstructions": [
                    {
                      "scopeId": "scopeId"
                    }
                  ]
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
  void shouldRejectModifyProcessInstanceWithTerminateInstructionsNoElement() {
    // given
    final var request =
        """
            {
              "terminateInstructions": [
                {}
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"At least one of [elementId, elementInstanceKey] is required.",
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
  void shouldRejectModifyProcessInstanceWithTerminateInstructionsTooManyElements() {
    // given
    final var request =
        """
            {
              "terminateInstructions": [
                {
                  "elementId": "elementId",
                  "elementInstanceKey": "123456"
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Only one of [elementId, elementInstanceKey] is allowed.",
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
  void shouldRejectModifyProcessInstanceWithMoveInstructionsUnexpectedAncestorScopeType() {
    // given
    final var request =
        """
            {
              "moveInstructions": [
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "elementId"
                  },
                  "targetElementId": "elementId2",
                  "ancestorScopeInstruction": {
                    "ancestorScopeType": "unknown"
                  }
                }
              ],
              "operationReference": 123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail": "Cannot map value 'unknown' for type 'ancestorScopeInstruction'. \
            Use any of the following values: [direct, inferred, sourceParent]",
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
  void shouldRejectModifyProcessInstanceWithMoveInstructionsTargetElementNull() {
    // given
    final var request =
        """
            {
              "moveInstructions": [
                {
                  "sourceElementInstruction": {
                    "sourceType": "byId",
                    "sourceElementId": "elementId"
                  }
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
                "detail": "No targetElementId provided.",
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
  void shouldRejectModifyProcessInstanceWithMoveInstructionsSourceElementNull() {
    // given
    final var request =
        """
            {
              "moveInstructions": [
                {
                  "targetElementId": "elementId"
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
                "detail": "No sourceElementInstruction provided.",
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
        List.of(
            new SequenceFlowEntity("pi1_sequenceFlow1", "node1", 1L, 37L, 1L, "pd1", "<default>"));
    when(processInstanceServices.sequenceFlows(processInstanceKey)).thenReturn(sequenceFlows);
    final var response =
        """
            {"items":[
              {
                "sequenceFlowId": "pi1_sequenceFlow1",
                "processInstanceKey": "1",
                "rootProcessInstanceKey": "37",
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
                        3751799814751237L,
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
                "rootProcessInstanceKey": "3751799814751237",
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

  @Test
  void shouldDeleteProcessInstanceBatchOperation() {
    // given
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(123L);
    record.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);

    when(processInstanceServices.deleteProcessInstancesBatchOperation(
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
        .uri("/v2/process-instances/deletion")
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
          {"batchOperationKey":"123","batchOperationType":"DELETE_PROCESS_INSTANCE"}
        """,
            JsonCompareMode.STRICT);

    verify(processInstanceServices)
        .deleteProcessInstancesBatchOperation(any(ProcessInstanceFilter.class));
  }

  @Test
  void shouldRejectDeleteProcessInstanceBatchOperationWithNoRequestBody() {
    // given
    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"Bad Request",
                "status":400,
                "detail":"Required request body is missing",
                "instance":"/v2/process-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/deletion")
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
  void shouldRejectDeleteProcessInstanceBatchOperationWithEmptyRequestBody() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No filter provided.",
                "instance":"/v2/process-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/deletion")
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
  void shouldRejectDeleteProcessInstanceBatchOperationWithEmptyFilter() {
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
                "instance":"/v2/process-instances/deletion"
             }""";

    // when / then
    webClient
        .post()
        .uri("/v2/process-instances/deletion")
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
  void shouldDeleteProcessInstance() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);

    when(processInstanceServices.deleteProcessInstance(1L, 123L))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
            {
              "operationReference": 123
            }""";

    // when / then
    webClient
        .post()
        .uri(DELETE_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldDeleteProcessInstanceWithNoBody() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);

    when(processInstanceServices.deleteProcessInstance(1L, null))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .post()
        .uri(DELETE_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldDeleteProcessInstanceWithEmptyBody() {
    // given
    final var record = new HistoryDeletionRecord();
    record.setResourceKey(123L);
    record.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);

    when(processInstanceServices.deleteProcessInstance(1L, null))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var request =
        """
        {}""";

    // when / then
    webClient
        .post()
        .uri(DELETE_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void shouldRejectDeleteProcessInstanceOnProcessInstanceNotFound() {
    // given
    when(processInstanceServices.deleteProcessInstance(1L, null))
        .thenReturn(
            CompletableFuture.failedFuture(
                new io.camunda.service.exception.ServiceException(
                    "Process Instance with key '1' not found",
                    io.camunda.service.exception.ServiceException.Status.NOT_FOUND)));

    final var expectedBody =
        """
            {
                "type": "about:blank",
                "title": "NOT_FOUND",
                "status": 404,
                "detail": "Process Instance with key '1' not found",
                "instance": "/v2/process-instances/1/deletion"
            }""";

    // when / then
    webClient
        .post()
        .uri(DELETE_PROCESS_URL.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }
}
