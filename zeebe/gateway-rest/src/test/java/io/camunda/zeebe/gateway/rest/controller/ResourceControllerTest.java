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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(ResourceController.class)
public class ResourceControllerTest extends RestControllerTest {

  static final String RESOURCES_BASE_URL = "/v2";
  static final String DEPLOY_RESOURCES_ENDPOINT = RESOURCES_BASE_URL + "/deployments";
  static final String DELETE_RESOURCE_ENDPOINT = RESOURCES_BASE_URL + "/resources/%s/deletion";

  @MockBean ResourceServices resourceServices;
  @MockBean MultiTenancyConfiguration multiTenancyCfg;
  @Captor ArgumentCaptor<DeployResourcesRequest> deployRequestCaptor;
  @Captor ArgumentCaptor<ResourceDeletionRequest> deleteRequestCaptor;

  @BeforeEach
  void setup() {
    when(resourceServices.withAuthentication(any(Authentication.class)))
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
                """);
  }

  @Test
  void shouldDeployResourceWithMultitenancyDisabled() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(false);
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
                """);
  }

  @Test
  void shouldRejectDeployResourcesWithUnauthorizedTenantWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(true);

    final var filename = "process.bpmn";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};
    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("resources", content).contentType(contentType).filename(filename);
    multipartBodyBuilder.part("tenantId", "unauthorizedTenant");

    // when then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(DEPLOY_RESOURCES_ENDPOINT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(multipartBodyBuilder.build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized());
    response
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 401,
              "title": "UNAUTHORIZED",
              "detail": "Expected to handle request Deploy Resources with tenant identifier 'unauthorizedTenant', but the user is not authorized for that tenant",
              "instance": "%s"
            }"""
                .formatted(DEPLOY_RESOURCES_ENDPOINT));
    verifyNoInteractions(resourceServices);
  }

  @Test
  void shouldDeployResourceWithMultitenancyEnabled() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
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
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(DEPLOY_RESOURCES_ENDPOINT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(multipartBodyBuilder.build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk());

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
                """);
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
                """);
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
  void shouldDeleteResource() {
    // given
    when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ResourceDeletionRecord()));

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
        .isNoContent();

    Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
    final var capturedRequest = deleteRequestCaptor.getValue();
    assertThat(capturedRequest.resourceKey()).isEqualTo(1);
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldDeleteResourceWithNoBody() {
    // given
    when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ResourceDeletionRecord()));

    // when/then
    webClient
        .post()
        .uri(DELETE_RESOURCE_ENDPOINT.formatted("1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
    final var capturedRequest = deleteRequestCaptor.getValue();
    assertThat(capturedRequest.resourceKey()).isEqualTo(1L);
    assertThat(capturedRequest.operationReference()).isNull();
  }

  @Test
  void shouldDeleteResourceWithEmptyBody() {
    // given
    when(resourceServices.deleteResource(any(ResourceDeletionRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ResourceDeletionRecord()));

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
        .isNoContent();

    Mockito.verify(resourceServices).deleteResource(deleteRequestCaptor.capture());
    final var capturedRequest = deleteRequestCaptor.getValue();
    assertThat(capturedRequest.resourceKey()).isEqualTo(1);
    assertThat(capturedRequest.operationReference()).isNull();
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
        .json(expectedBody);
  }
}
