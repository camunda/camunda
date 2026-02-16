/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.zeebe.util.VersionUtil;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Configuration for multiple MCP server instances with their own transports.
 *
 * <p>This configuration creates an MCP server programmatically:
 *
 * <ul>
 *   <li>{@code /mcp/cluster} - Static tools from {@link CamundaMcpTool} annotated methods
 * </ul>
 *
 * <p>Each server has its own transport provider and tool specifications, allowing independent
 * configuration and management.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration",
      "org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration",
      "org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebFluxAutoConfiguration",
      "org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration"
    })
@ConditionalOnMcpGatewayEnabled
public class McpServersConfiguration {

  @Bean
  public McpJsonMapper mcpJsonMapper(final ObjectMapper objectMapper) {
    return new JacksonMcpJsonMapper(objectMapper);
  }

  /**
   * Transport provider for the cluster MCP server at {@code /mcp/cluster}.
   *
   * <p>Handles MCP protocol messages for cluster-wide operations and general Camunda API access.
   */
  @Bean(name = "clusterTransportProvider")
  public WebMvcStatelessServerTransport clusterTransportProvider(
      final McpJsonMapper mcpJsonMapper) {
    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(mcpJsonMapper)
        .messageEndpoint("/mcp/cluster")
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> clusterRouterFunction(
      @Qualifier("clusterTransportProvider")
          final WebMvcStatelessServerTransport clusterTransportProvider) {
    return clusterTransportProvider.getRouterFunction();
  }

  /**
   * MCP server for cluster operations at {@code /mcp/cluster}.
   *
   * <p>Provides static tools defined via {@link CamundaMcpTool} annotations.
   */
  @Bean
  public McpStatelessSyncServer clusterMcpServer(
      @Qualifier("clusterTransportProvider") final McpStatelessServerTransport clusterTransport,
      @Qualifier("mcpGatewayToolSpecifications")
          final List<SyncToolSpecification> toolSpecifications) {

    final var capabilities = McpSchema.ServerCapabilities.builder().tools(false).build();

    return McpServer.sync(clusterTransport)
        .serverInfo("Camunda 8 Orchestration API MCP Server", VersionUtil.getVersion())
        .capabilities(capabilities)
        .tools(toolSpecifications)
        .immediateExecution(true)
        .build();
  }

  /**
   * Transport provider for the cluster MCP server at {@code /mcp/cluster}.
   *
   * <p>Handles MCP protocol messages for cluster-wide operations and general Camunda API access.
   */
  @Bean(name = "processesTransportProvider")
  public WebMvcStatelessServerTransport processesTransportProvider(
      final McpJsonMapper mcpJsonMapper) {
    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(mcpJsonMapper)
        .messageEndpoint("/mcp/processes")
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> processesRouterFunction(
      @Qualifier("processesTransportProvider")
          final WebMvcStatelessServerTransport processesTransportProvider) {
    return processesTransportProvider.getRouterFunction();
  }

  /**
   * MCP server for cluster operations at {@code /mcp/cluster}.
   *
   * <p>Provides static tools defined via {@link CamundaMcpTool} annotations.
   */
  @Bean
  public McpStatelessSyncServer processesMcpServer(
      @Qualifier("processesTransportProvider") final McpStatelessServerTransport processesTransport,
      @Qualifier("mcpGatewayToolSpecifications")
          final List<SyncToolSpecification> toolSpecifications) {

    final var capabilities = McpSchema.ServerCapabilities.builder().tools(false).build();

    return McpServer.sync(processesTransport)
        .serverInfo("Camunda 8 Orchestration API Processes MCP Server", VersionUtil.getVersion())
        .capabilities(capabilities)
        .tools(toolSpecifications)
        .immediateExecution(true)
        .build();
  }
}
