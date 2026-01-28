/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.gateway.protocol.model.DeploymentResult;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {ResourceTools.class})
class ResourceToolsTest extends ToolsTest {

  private static final String BPMN_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn/>";
  private static final String BPMN_CONTENT_BASE64 =
      Base64.getEncoder().encodeToString(BPMN_CONTENT.getBytes());

  @MockitoBean private ResourceServices resourceServices;
  @MockitoBean private MultiTenancyConfiguration multiTenancyConfiguration;

  @Autowired private ObjectMapper objectMapper;
  @Captor private ArgumentCaptor<DeployResourcesRequest> deployRequestCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(resourceServices);
    when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);
  }

  private DeploymentRecord createDeploymentRecord() {
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord.setDeploymentKey(123L);
    deploymentRecord.setTenantId("<default>");

    deploymentRecord
        .processesMetadata()
        .add()
        .setBpmnProcessId("testProcess")
        .setResourceName("test.bpmn")
        .setVersion(1)
        .setKey(456L)
        .setDeploymentKey(123L)
        .setTenantId("<default>")
        .setChecksum(io.camunda.zeebe.util.buffer.BufferUtil.wrapString("checksum"));

    return deploymentRecord;
  }

  private void assertExampleDeploymentResult(final DeploymentResult deployment) {
    assertThat(deployment.getDeploymentKey()).isEqualTo("123");
    assertThat(deployment.getTenantId()).isEqualTo("<default>");
    assertThat(deployment.getDeployments()).hasSize(1);

    final var processDeployment = deployment.getDeployments().getFirst();
    assertThat(processDeployment.getProcessDefinition()).isNotNull();
    assertThat(processDeployment.getProcessDefinition().getProcessDefinitionId())
        .isEqualTo("testProcess");
    assertThat(processDeployment.getProcessDefinition().getResourceName()).isEqualTo("test.bpmn");
    assertThat(processDeployment.getProcessDefinition().getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(processDeployment.getProcessDefinition().getProcessDefinitionKey()).isEqualTo("456");
    assertThat(processDeployment.getProcessDefinition().getTenantId()).isEqualTo("<default>");
  }

  @Nested
  class DeployResources {

    @Test
    void shouldDeployResources() {
      // given
      final var deploymentRecord = createDeploymentRecord();
      when(resourceServices.deployResources(any(DeployResourcesRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(deploymentRecord));

      final Map<String, String> resources = Map.of("test.bpmn", BPMN_CONTENT_BASE64);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var deployment =
          objectMapper.convertValue(result.structuredContent(), DeploymentResult.class);
      assertExampleDeploymentResult(deployment);

      verify(resourceServices).deployResources(deployRequestCaptor.capture());
      final var capturedRequest = deployRequestCaptor.getValue();
      assertThat(capturedRequest.resources()).containsKey("test.bpmn");
      assertThat(capturedRequest.resources().get("test.bpmn")).isEqualTo(BPMN_CONTENT.getBytes());
      assertThat(capturedRequest.tenantId()).isEqualTo("<default>");
    }

    @Test
    void shouldDeployResourcesWithTenantId() {
      // given
      final var deploymentRecord = createDeploymentRecord();
      deploymentRecord.setTenantId("custom-tenant");
      when(resourceServices.deployResources(any(DeployResourcesRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(deploymentRecord));

      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);

      final Map<String, String> resources = Map.of("test.bpmn", BPMN_CONTENT_BASE64);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources, "tenantId", "custom-tenant"))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      verify(resourceServices).deployResources(deployRequestCaptor.capture());
      final var capturedRequest = deployRequestCaptor.getValue();
      assertThat(capturedRequest.tenantId()).isEqualTo("custom-tenant");
    }

    @Test
    void shouldFailDeployResourcesOnServiceException() {
      // given
      when(resourceServices.deployResources(any(DeployResourcesRequest.class)))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new ServiceException("Deployment failed", Status.INVALID_STATE)));

      final Map<String, String> resources = Map.of("test.bpmn", BPMN_CONTENT_BASE64);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Deployment failed");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getTitle()).isEqualTo("INVALID_STATE");
    }

    @Test
    void shouldFailDeployResourcesOnEmptyResources() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", Map.of()))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text()).contains("Resources must not be empty."));
    }

    @Test
    void shouldFailDeployResourcesOnInvalidBase64() {
      // given
      final Map<String, String> resources = Map.of("test.bpmn", "not-valid-base64!!!");

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).contains("Invalid base64 encoding for resource");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldFailDeployResourcesOnMultiTenancyViolation() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);
      final Map<String, String> resources = Map.of("test.bpmn", BPMN_CONTENT_BASE64);

      // when - no tenantId provided when multi-tenancy is enabled
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).contains("Deploy Resources");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldDeployMultipleResources() {
      // given
      final var deploymentRecord = createDeploymentRecord();
      when(resourceServices.deployResources(any(DeployResourcesRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(deploymentRecord));

      final Map<String, String> resources =
          Map.of(
              "process1.bpmn", BPMN_CONTENT_BASE64,
              "process2.bpmn", BPMN_CONTENT_BASE64);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("deployResources")
                  .arguments(Map.of("resources", resources))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(resourceServices).deployResources(deployRequestCaptor.capture());
      final var capturedRequest = deployRequestCaptor.getValue();
      assertThat(capturedRequest.resources()).hasSize(2);
      assertThat(capturedRequest.resources()).containsKeys("process1.bpmn", "process2.bpmn");
    }
  }
}
