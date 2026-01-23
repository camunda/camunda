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

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.ResourceServices.ResourceFetchRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ResourceController.class)
public class ResourceControllerTest extends RestControllerTest {

  static final String RESOURCES_BASE_URL = "/v2";
  static final String DEPLOY_RESOURCES_ENDPOINT = RESOURCES_BASE_URL + "/deployments";
  static final String DELETE_RESOURCE_ENDPOINT = RESOURCES_BASE_URL + "/resources/%s/deletion";
  static final String GET_RESOURCE_ENDPOINT = RESOURCES_BASE_URL + "/resources/%s";
  static final String GET_RESOURCE_CONTENT_ENDPOINT = RESOURCES_BASE_URL + "/resources/%s/content";

  @MockitoBean ResourceServices resourceServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @Captor ArgumentCaptor<DeployResourcesRequest> deployRequestCaptor;
  @Captor ArgumentCaptor<ResourceDeletionRequest> deleteRequestCaptor;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(resourceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(resourceServices);
  }

  @Test
  void shouldDeployASingleResource() {
    // given
    final var filename = "process.bpmn";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var mockedResponse = new DeploymentRecord().setDeploymentKey(123);
    mockedResponse
        .processesMetadata()
        .add()
        .setResourceName(filename)
        .setBpmnProcessId("processId")
        .setDeploymentKey(123L)
        .setVersion(1)
        .setKey(123456L)
        .setChecksum(BufferUtil.wrapString("checksum"));
    when(resourceServices.deployResources(any()))
        .thenReturn(CompletableFuture.completedFuture(mockedResponse));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("resources", content).contentType(contentType).filename(filename);

    // when/then
    final var response =
        webClient
            .post()
            .uri(DEPLOY_RESOURCES_ENDPOINT)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipartBodyBuilder.build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk();

    verify(resourceServices).deployResources(deployRequestCaptor.capture());
    final var capturedRequest = deployRequestCaptor.getValue();
    assertThat(capturedRequest.resources()).isNotEmpty();
    assertThat(capturedRequest.resources()).size().isEqualTo(1);

    response
        .expectBody()
        .json(
            """
                 {
                    "deploymentKey":"123",
                    "deployments":[
                       {
                          "processDefinition":{
                             "processDefinitionId":"processId",
                             "processDefinitionVersion":1,
                             "processDefinitionKey":"123456",
                             "resourceName":"process.bpmn",
                             "tenantId":"<default>"
                          }
                       }
                    ],
                    "tenantId":"<default>"
                 }
                """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldDeployResourceWithMultitenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    final var filename = "process.bpmn";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var mockedResponse = new DeploymentRecord().setDeploymentKey(123);
    mockedResponse
        .processesMetadata()
        .add()
        .setResourceName(filename)
        .setBpmnProcessId("processId")
        .setDeploymentKey(123L)
        .setVersion(1)
        .setKey(123456L)
        .setChecksum(BufferUtil.wrapString("checksum"));
    when(resourceServices.deployResources(any()))
        .thenReturn(CompletableFuture.completedFuture(mockedResponse));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("resources", content).contentType(contentType).filename(filename);

    // when/then
    final var response =
        webClient
            .post()
            .uri(DEPLOY_RESOURCES_ENDPOINT)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipartBodyBuilder.build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk();

    verify(resourceServices).deployResources(deployRequestCaptor.capture());
    final var capturedRequest = deployRequestCaptor.getValue();
    assertThat(capturedRequest.resources()).isNotEmpty();
    assertThat(capturedRequest.resources()).size().isEqualTo(1);

    response
        .expectBody()
        .json(
            """
                 {
                    "deploymentKey":"123",
                    "deployments":[
                       {
                          "processDefinition":{
                             "processDefinitionId":"processId",
                             "processDefinitionVersion":1,
                             "processDefinitionKey":"123456",
                             "resourceName":"process.bpmn",
                             "tenantId":"<default>"
                          }
                       }
                    ],
                    "tenantId":"<default>"
                 }
                """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldDeployResourceWithMultitenancyEnabled() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var filename = "process.bpmn";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var mockedResponse = new DeploymentRecord().setDeploymentKey(123);
    mockedResponse
        .processesMetadata()
        .add()
        .setResourceName(filename)
        .setBpmnProcessId("processId")
        .setDeploymentKey(123L)
        .setVersion(1)
        .setKey(123456L)
        .setTenantId("tenantId")
        .setChecksum(BufferUtil.wrapString("checksum"));
    when(resourceServices.deployResources(any()))
        .thenReturn(CompletableFuture.completedFuture(mockedResponse));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("resources", content).contentType(contentType).filename(filename);
    multipartBodyBuilder.part("tenantId", "tenantId");

    // when/then
    final var response =
        webClient
            .post()
            .uri(DEPLOY_RESOURCES_ENDPOINT)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipartBodyBuilder.build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk();

    verify(resourceServices).deployResources(deployRequestCaptor.capture());
    final var capturedRequest = deployRequestCaptor.getValue();
    assertThat(capturedRequest.resources()).isNotEmpty();
    assertThat(capturedRequest.resources()).size().isEqualTo(1);

    response
        .expectBody()
        .json(
            """
                 {
                    "deploymentKey":"123",
                    "deployments":[
                       {
                          "processDefinition":{
                             "processDefinitionId":"processId",
                             "processDefinitionVersion":1,
                             "processDefinitionKey":"123456",
                             "resourceName":"process.bpmn",
                             "tenantId":"tenantId"
                          }
                       }
                    ],
                    "tenantId":"<default>"
                 }
                """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldDeployMultipleResources() {
    // given
    final var filename = "process.bpmn";
    final var secondFilename = "second.bpmn";
    final var formFilename = "test.form";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};
    final var content2 = new byte[] {5, 6, 7};
    final var content3 = new byte[] {8, 9, 1};

    final var mockedResponse = new DeploymentRecord().setDeploymentKey(123);
    mockedResponse
        .processesMetadata()
        .add()
        .setResourceName(filename)
        .setBpmnProcessId("processId")
        .setDeploymentKey(123L)
        .setVersion(1)
        .setKey(123456L)
        .setChecksum(BufferUtil.wrapString("checksum"));
    mockedResponse
        .processesMetadata()
        .add()
        .setResourceName(secondFilename)
        .setBpmnProcessId("secondProcessId")
        .setDeploymentKey(456L)
        .setVersion(1)
        .setKey(7890123L)
        .setChecksum(BufferUtil.wrapString("checksum"));
    mockedResponse
        .formMetadata()
        .add()
        .setResourceName(filename)
        .setDeploymentKey(123L)
        .setVersion(1)
        .setFormId("formId")
        .setFormKey(123456L)
        .setChecksum(BufferUtil.wrapString("checksum"));
    when(resourceServices.deployResources(any()))
        .thenReturn(CompletableFuture.completedFuture(mockedResponse));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("resources", content).contentType(contentType).filename(filename);
    multipartBodyBuilder
        .part("resources", content2)
        .contentType(contentType)
        .filename(secondFilename);
    multipartBodyBuilder
        .part("resources", content3)
        .contentType(contentType)
        .filename(formFilename);

    // when/then
    final var response =
        webClient
            .post()
            .uri(DEPLOY_RESOURCES_ENDPOINT)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipartBodyBuilder.build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk();

    verify(resourceServices).deployResources(deployRequestCaptor.capture());
    final var capturedRequest = deployRequestCaptor.getValue();
    assertThat(capturedRequest.resources()).isNotEmpty();
    assertThat(capturedRequest.resources()).size().isEqualTo(3);

    response
        .expectBody()
        .json(
            """
                 {
                    "deploymentKey":"123",
                    "deployments":[
                       {
                          "processDefinition":{
                             "processDefinitionId":"processId",
                             "processDefinitionVersion":1,
                             "processDefinitionKey":"123456",
                             "resourceName":"process.bpmn",
                             "tenantId":"<default>"
                          }
                       },
                       {
                          "processDefinition":{
                             "processDefinitionId":"secondProcessId",
                             "processDefinitionVersion":1,
                             "processDefinitionKey":"7890123",
                             "resourceName":"second.bpmn",
                             "tenantId":"<default>"
                          }
                       },
                       {
                          "form":{
                             "formId":"formId",
                             "version":1,
                             "formKey":"123456",
                             "resourceName":"process.bpmn",
                             "tenantId":"<default>"
                          }
                       }
                    ],
                    "tenantId":"<default>"
                 }
                """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectEmptyResources() {
    // given
    final var multipartBodyBuilder = new MultipartBodyBuilder();

    // when/then
    webClient
        .post()
        .uri(DEPLOY_RESOURCES_ENDPOINT)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldGetResource() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceRecord()
                    .setResourceName("test.rpa")
                    .setResourceId("test")
                    .setVersion(2)
                    .setVersionTag("v2.0")
                    .setTenantId("tenant-1")
                    .setResourceKey(1)));

    // when / then
    webClient
        .get()
        .uri(GET_RESOURCE_ENDPOINT.formatted(1))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
          {
            "resourceName": "test.rpa",
            "version": 2,
            "versionTag": "v2.0",
            "resourceId": "test",
            "tenantId": "tenant-1",
            "resourceKey": "1"
          }
          """,
            JsonCompareMode.STRICT);
  }

  @Test
  void getResourceShouldYieldNotFoundWhenResourceNotFound() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        ResourceIntent.FETCH, 1L, RejectionType.NOT_FOUND, "Resource not found"))));
    final var url = GET_RESOURCE_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 404,
              "title": "NOT_FOUND",
              "detail": "Command 'FETCH' rejected with code 'NOT_FOUND': Resource not found",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Test
  void getResourceShouldYieldInternalServerErrorForProcessingErrorRejection() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        ResourceIntent.FETCH,
                        1L,
                        RejectionType.PROCESSING_ERROR,
                        "something went wrong"))));
    final var url = GET_RESOURCE_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 500,
              "title": "INTERNAL",
              "detail": "Command 'FETCH' rejected with code 'PROCESSING_ERROR': something went wrong",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Test
  void getResourceShouldYieldInternalServerErrorForBrokerError() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.INTERNAL_ERROR, "something went wrong"))));
    final var url = GET_RESOURCE_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 500,
              "title": "INTERNAL",
              "detail": "Unexpected error occurred between gateway and broker (code: INTERNAL_ERROR) (message: something went wrong)",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetResourceContent() {
    // given
    final var content =
        """
        {
          "id": "test",
          "name": "test RPA script",
          "script": "foo"
        }
        """;
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceRecord().setResource(BufferUtil.wrapString(content))));

    // when / then
    webClient
        .get()
        .uri(GET_RESOURCE_CONTENT_ENDPOINT.formatted(1))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(content, JsonCompareMode.STRICT);
  }

  @Test
  void getResourceContentShouldYieldNotFoundWhenResourceNotFound() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        ResourceIntent.FETCH, 1L, RejectionType.NOT_FOUND, "Resource not found"))));
    final var url = GET_RESOURCE_CONTENT_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 404,
              "title": "NOT_FOUND",
              "detail": "Command 'FETCH' rejected with code 'NOT_FOUND': Resource not found",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Test
  void getResourceContentShouldYieldInternalServerErrorForProcessingErrorRejection() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        ResourceIntent.FETCH,
                        1L,
                        RejectionType.PROCESSING_ERROR,
                        "something went wrong"))));
    final var url = GET_RESOURCE_CONTENT_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 500,
              "title": "INTERNAL",
              "detail": "Command 'FETCH' rejected with code 'PROCESSING_ERROR': something went wrong",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Test
  void getResourceContentShouldYieldInternalServerErrorForBrokerError() {
    // given
    when(resourceServices.fetchResource(new ResourceFetchRequest(1)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.INTERNAL_ERROR, "something went wrong"))));
    final var url = GET_RESOURCE_CONTENT_ENDPOINT.formatted(1);

    // when / then
    webClient
        .get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 500,
              "title": "INTERNAL",
              "detail": "Unexpected error occurred between gateway and broker (code: INTERNAL_ERROR) (message: something went wrong)",
              "instance": "%s"
            }
            """
                .formatted(url),
            JsonCompareMode.STRICT);
  }

  @Nested
  class DeleteResourceTests {

    @Test
    void shouldDeleteResource() {
      // given
      final var resourceKey = 1L;
      when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new ResourceDeletionRecord().setResourceKey(resourceKey)));

      final var request =
          """
              {
                "operationReference": 123
              }""";

      // when/then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "resourceKey": "1"
              }
              """,
              JsonCompareMode.STRICT);

      Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
      final var capturedRequest = deleteRequestCaptor.getValue();
      assertThat(capturedRequest.resourceKey()).isEqualTo(resourceKey);
      assertThat(capturedRequest.operationReference()).isEqualTo(123L);
      assertThat(capturedRequest.deleteHistory()).isFalse();
    }

    @Test
    void shouldDeleteResourceWithNoBody() {
      // given
      final var resourceKey = 1L;
      when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new ResourceDeletionRecord().setResourceKey(resourceKey)));

      // when/then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "resourceKey": "1"
              }
              """,
              JsonCompareMode.STRICT);

      Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
      final var capturedRequest = deleteRequestCaptor.getValue();
      assertThat(capturedRequest.resourceKey()).isEqualTo(resourceKey);
      assertThat(capturedRequest.operationReference()).isNull();
      assertThat(capturedRequest.deleteHistory()).isFalse();
    }

    @Test
    void shouldDeleteResourceWithEmptyRequestBody() {
      // given
      final var resourceKey = 1L;
      when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new ResourceDeletionRecord().setResourceKey(resourceKey)));

      final var request =
          """
              {}""";

      // when/then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "resourceKey": "1"
              }
              """,
              JsonCompareMode.STRICT);

      Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
      final var capturedRequest = deleteRequestCaptor.getValue();
      assertThat(capturedRequest.resourceKey()).isEqualTo(resourceKey);
      assertThat(capturedRequest.operationReference()).isNull();
      assertThat(capturedRequest.deleteHistory()).isFalse();
    }

    @Test
    void shouldDeleteResourceWithDeleteHistory() {
      // given
      final var resourceKey = 1L;
      final var deletionRecord =
          new ResourceDeletionRecord()
              .setResourceKey(resourceKey)
              .setDeleteHistory(true)
              .setBatchOperationKey(999L)
              .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);

      when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(deletionRecord));

      final var request =
          """
              {
                "deleteHistory": true
              }""";

      // when/then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "resourceKey": "1",
                "batchOperation": {
                  "batchOperationKey": "999",
                  "batchOperationType": "DELETE_PROCESS_INSTANCE"
                }
              }
              """,
              JsonCompareMode.STRICT);

      Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
      final var capturedRequest = deleteRequestCaptor.getValue();
      assertThat(capturedRequest.resourceKey()).isEqualTo(resourceKey);
      assertThat(capturedRequest.deleteHistory()).isTrue();
    }

    @Test
    void shouldDeleteResourceWithAllFields() {
      // given
      final var resourceKey = 1L;
      final var deletionRecord =
          new ResourceDeletionRecord()
              .setResourceKey(resourceKey)
              .setDeleteHistory(true)
              .setBatchOperationKey(555L)
              .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);

      when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(deletionRecord));

      final var request =
          """
              {
                "operationReference": 123,
                "deleteHistory": true
              }""";

      // when/then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "resourceKey": "1",
                "batchOperation": {
                  "batchOperationKey": "555",
                  "batchOperationType": "DELETE_PROCESS_INSTANCE"
                }
              }
              """,
              JsonCompareMode.STRICT);

      Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
      final var capturedRequest = deleteRequestCaptor.getValue();
      assertThat(capturedRequest.resourceKey()).isEqualTo(resourceKey);
      assertThat(capturedRequest.operationReference()).isEqualTo(123L);
      assertThat(capturedRequest.deleteHistory()).isTrue();
    }

    @Test
    void shouldRejectDeleteResourceWithOperationReferenceNotValid() {
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
                  "instance":"/v2/resources/1/deletion"
               }""";

      // when / then
      webClient
          .post()
          .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
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
}
