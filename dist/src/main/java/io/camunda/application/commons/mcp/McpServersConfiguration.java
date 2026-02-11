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
import io.camunda.gateway.mcp.server.DynamicToolMcpStatelessSyncServer;
import io.camunda.gateway.mcp.server.DynamicToolsMcpServer;
import io.camunda.gateway.mcp.tool.process.dynamic.DynamicProcessToolProvider;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Configuration for multiple MCP server instances with their own transports.
 *
 * <p>This configuration creates two separate MCP servers programmatically:
 *
 * <ul>
 *   <li>{@code /mcp/cluster} - Static tools from {@link CamundaMcpTool} annotated methods
 *   <li>{@code /mcp/processes} - Dynamic tools from process definitions based on user permissions
 * </ul>
 *
 * <p>Each server has its own transport provider and tool specifications, allowing independent
 * configuration and management. This configuration excludes Spring AI's default {@code
 * McpServerAutoConfiguration} to prevent conflicts with programmatic server creation.
 */
@Configuration(proxyBeanMethods = false)
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

    final var capabilities = McpSchema.ServerCapabilities.builder().tools(true).logging().build();

    return McpServer.sync(clusterTransport)
        .serverInfo("Camunda 8 Orchestration API MCP Server", VersionUtil.getVersion())
        .capabilities(capabilities)
        .tools(toolSpecifications)
        .build();
  }

  /**
   * Transport provider for the processes MCP server at {@code /mcp/processes}.
   *
   * <p>Handles MCP protocol messages for process-specific operations with dynamic tool generation.
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
   * Dynamic process tool provider for generating MCP tools from process definitions.
   *
   * <p>Instantiates the tool provider with required services for querying process definitions and
   * creating process instances based on user permissions.
   */
  @Bean
  public DynamicProcessToolProvider dynamicProcessToolProvider(
      final ProcessDefinitionServices processDefinitionServices,
      final ProcessInstanceServices processInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final MultiTenancyConfiguration multiTenancyConfiguration,
      final McpJsonMapper mcpJsonMapper) {
    return new DynamicProcessToolProvider(
        processDefinitionServices,
        processInstanceServices,
        authenticationProvider,
        multiTenancyConfiguration,
        mcpJsonMapper);
  }

  /**
   * MCP server for processes at {@code /mcp/processes}.
   *
   * <p>Provides tools generated dynamically from the latest versions of process definitions.
   */
  @Bean
  public DynamicToolMcpStatelessSyncServer processesMcpServer(
      @Qualifier("processesTransportProvider") final McpStatelessServerTransport processesTransport,
      final DynamicProcessToolProvider toolProvider) {

    final var capabilities = McpSchema.ServerCapabilities.builder().tools(true).logging().build();

    return DynamicToolsMcpServer.sync(processesTransport)
        .serverInfo("Camunda 8 Orchestration API Processes MCP Server", VersionUtil.getVersion())
        .capabilities(capabilities)
        .toolsProvider(toolProvider)
        .build();
  }
}
