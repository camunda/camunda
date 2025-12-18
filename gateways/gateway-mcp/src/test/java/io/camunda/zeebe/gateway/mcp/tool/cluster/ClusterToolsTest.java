/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.Broker;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.service.TopologyServices.Health;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Role;
import io.camunda.service.TopologyServices.Topology;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.mcp.tool.ToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {ClusterTools.class})
class ClusterToolsTest extends ToolsTest {

  @MockitoBean private TopologyServices topologyServices;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(topologyServices);
  }

  @ParameterizedTest
  @EnumSource(ClusterStatus.class)
  void shouldLoadClusterStatus(final ClusterStatus status) {
    // given
    when(topologyServices.getStatus()).thenReturn(CompletableFuture.completedFuture(status));

    // when
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getClusterStatus").build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent -> assertThat(textContent.text()).isEqualTo(status.name()));
  }

  @Test
  void shouldFailLoadingClusterStatusOnException() {
    // given
    when(topologyServices.getStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Expected failure", Status.INVALID_STATE)));

    // when
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getClusterStatus").build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.content()).isEmpty();
    assertThat(result.structuredContent()).isNotNull();

    final var problemDetail =
        objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
    assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getTitle()).isEqualTo("INVALID_STATE");
  }

  @Test
  void shouldLoadTopology() {
    // given
    final var version = "8.9.10";
    final var expectedTopology =
        new Topology(
            List.of(
                new Broker(
                    0,
                    "localhost",
                    26501,
                    List.of(new Partition(1, Role.LEADER, Health.HEALTHY)),
                    version),
                new Broker(
                    1,
                    "localhost",
                    26502,
                    List.of(new Partition(1, Role.FOLLOWER, Health.HEALTHY)),
                    version),
                new Broker(
                    2,
                    "localhost",
                    26503,
                    List.of(new Partition(1, Role.INACTIVE, Health.UNHEALTHY)),
                    version)),
            "cluster-id",
            3,
            1,
            3,
            version,
            1L);

    when(topologyServices.getTopology())
        .thenReturn(CompletableFuture.completedFuture(expectedTopology));

    // when
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getTopology").build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var topology = objectMapper.convertValue(result.structuredContent(), Topology.class);
    assertThat(topology).usingRecursiveComparison().isEqualTo(expectedTopology);
  }

  @Test
  void shouldFailLoadingTopologyOnException() {
    // given
    when(topologyServices.getTopology())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Expected failure", Status.INVALID_STATE)));

    // when
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getTopology").build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.content()).isEmpty();
    assertThat(result.structuredContent()).isNotNull();

    final var problemDetail =
        objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
    assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getTitle()).isEqualTo("INVALID_STATE");
  }
}
