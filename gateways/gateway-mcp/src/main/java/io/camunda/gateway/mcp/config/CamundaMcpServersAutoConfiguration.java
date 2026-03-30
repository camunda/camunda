/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.gateway.mcp.config.server.ToolRepositoryWebMvcStatelessServerTransport;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.zeebe.util.VersionUtil;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.List;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuration for multiple MCP server instances with their own transports.
 *
 * <p>This configuration creates MCP servers programmatically:
 *
 * <ul>
 *   <li>{@code /mcp/cluster} - Static tools from {@link CamundaMcpTool} annotated methods
 *   <li>{@code /mcp/processes} - Process definitions as tools based on permissions and deployments
 * </ul>
 *
 * <p>Each server has its own transport provider and tool resolution strategy, allowing independent
 * configuration and management.
 */
@AutoConfiguration
@ConditionalOnMcpGatewayEnabled
public class CamundaMcpServersAutoConfiguration {

  /**
   * Transport provider for the cluster MCP server at {@code /mcp/cluster}.
   *
   * <p>Handles MCP protocol messages for cluster-wide operations and general Camunda API access.
   *
   * <p>The MCP JsonMapper is provided by Spring AI auto-configuration. We tie into that to reuse
   * what Spring AI is using for tool schema definitions.
   *
   * @param jsonMapper the JSON mapper to use for MCP message serialization and deserialization
   */
  @Bean(name = "clusterTransportProvider")
  public WebMvcStatelessServerTransport clusterTransportProvider(
      @Qualifier("mcpServerJsonMapper") final JsonMapper jsonMapper) {
    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .messageEndpoint("/mcp/cluster")
        .build();
  }

  /**
   * Router function for the cluster MCP server to tie in with the web server stack.
   *
   * @param clusterTransportProvider the associated transport provider
   */
  @Bean
  public RouterFunction<ServerResponse> clusterRouterFunction(
      @Qualifier("clusterTransportProvider")
          final WebMvcStatelessServerTransport clusterTransportProvider) {
    return clusterTransportProvider.getRouterFunction();
  }

  /**
   * MCP server for general cluster-wide operations.
   *
   * <p>Provides static tools defined via {@link CamundaMcpTool} annotations.
   */
  @Bean
  public McpStatelessSyncServer clusterMcpServer(
      @Qualifier("clusterTransportProvider") final McpStatelessServerTransport clusterTransport,
      @Qualifier("clusterMcpToolSpecifications")
          final List<SyncToolSpecification> toolSpecifications) {

    final var capabilities =
        McpSchema.ServerCapabilities.builder()
            .tools(false)
            .resources(false, false)
            .prompts(false)
            .build();

    return McpServer.sync(clusterTransport)
        .serverInfo("Camunda 8 Orchestration API MCP Server", VersionUtil.getVersion())
        .instructions(
            """
          This server exposes APIs of a Camunda 8 Orchestration Cluster. All operations are
          scoped to the permissions of the authenticated user.

          Most tools return eventually consistent data. Recently written data may not appear
          immediately in subsequent reads.

          Process instances, incidents, and user tasks are related. A process instance can have
          active incidents and user tasks. Use the process instance key to correlate across
          domains. To understand the structure of a process, retrieve its BPMN XML.

          When starting a process instance with awaitCompletion, a timeout does NOT mean the
          process failed — the instance was created and continues running. Tag each instance
          (e.g. "mcp-tool:<operation>-<timestamp>") so you can find it later. Poll the instance
          state: if COMPLETED, retrieve result variables; if it has an incident, investigate
          using the process instance key. Variables can be retrieved at any time, including
          while the instance is still running.
          """)
        .capabilities(capabilities)
        .tools(toolSpecifications)
        .immediateExecution(true)
        .build();
  }

  /**
   * Transport provider for the processes MCP server at {@code /mcp/processes}.
   *
   * <p>Handles MCP protocol messages for processes exposed as tools.
   *
   * <p>The MCP JsonMapper is provided by Spring AI auto-configuration. We tie into that to reuse
   * what Spring AI is using for tool schema definitions.
   */
  @Bean(name = "processesTransportProvider")
  public ToolRepositoryWebMvcStatelessServerTransport processesTransportProvider(
      @Qualifier("mcpServerJsonMapper") final JsonMapper jsonMapper,
      @Qualifier("processesToolRepository") final ToolRepository toolRepository) {
    return new ToolRepositoryWebMvcStatelessServerTransport(
        jsonMapper, toolRepository, "/mcp/processes");
  }

  /**
   * Router function for the processes MCP server to tie in with the web server stack.
   *
   * @param processesTransportProvider the associated transport provider
   */
  @Bean
  public RouterFunction<ServerResponse> processesRouterFunction(
      @Qualifier("processesTransportProvider")
          final ToolRepositoryWebMvcStatelessServerTransport processesTransportProvider) {
    return processesTransportProvider.getRouterFunction();
  }

  /**
   * MCP server for processes as tools.
   *
   * <p>Resolves processes as MCP tools for each request based on the current request context and
   * current deployments.
   */
  @Bean
  public McpStatelessSyncServer processesMcpServer(
      @Qualifier("processesTransportProvider") final McpStatelessServerTransport processesTransport,
      @Qualifier("mcpServerJsonMapper") final JsonMapper jsonMapper) {

    final var capabilities =
        McpSchema.ServerCapabilities.builder()
            .tools(false)
            .resources(false, false)
            .prompts(false)
            .build();

    return McpServer.sync(processesTransport)
        .serverInfo("Camunda 8 Orchestration API Processes MCP Server", VersionUtil.getVersion())
        .instructions(
            """
          This server exposes Camunda 8 processes as tools. All operations are scoped to the
          permissions of the authenticated user.

          Tools are based on eventually consistent data, i.e., deployed process definitions that are
          configured to be exposed as MCP tools. Recently written data may not appear immediately
          in subsequent reads.

          Invoking a process exposed as tool starts a new process instance of the related process
          definition. The tool returns the internal key of the newly created instance for follow-up
          operations.
          """)
        .capabilities(capabilities)
        .immediateExecution(true)
        .build();
  }
}
