/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static io.camunda.spring.utils.PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANT_URI_PREFIX;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.server.RequestHandlerCustomizer;
import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.util.VersionUtil;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.List;
import java.util.Set;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuration for multiple MCP server instances with their own transports.
 *
 * <p>This configuration creates two MCP servers programmatically, each served from two URLs by a
 * single dual-path router function:
 *
 * <ul>
 *   <li>cluster server — all static tools from {@link CamundaMcpTool} annotated methods, at {@code
 *       /mcp/cluster} and {@code /physical-tenants/{physicalTenantId}/mcp/cluster}
 *   <li>processes server — process definitions as tools based on permissions and deployments, at
 *       {@code /mcp/processes} and {@code /physical-tenants/{physicalTenantId}/mcp/processes}
 * </ul>
 *
 * <p>The default URL stamps the {@link PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID default}
 * physical tenant; the {@code /physical-tenants/...} URL resolves and validates the tenant from the
 * path. Both URLs share one transport per server, since the transport handler reads only the
 * request body and is path-agnostic — the resolved tenant flows through {@link
 * PhysicalTenantContext}.
 */
@AutoConfiguration
@ConditionalOnMcpGatewayEnabled
public class CamundaMcpServersAutoConfiguration {

  /** Cluster MCP server endpoint. */
  private static final String CLUSTER_ENDPOINT = "/mcp/cluster";

  /** Processes MCP server endpoint. */
  private static final String PROCESSES_ENDPOINT = "/mcp/processes";

  private static final String CLUSTER_SERVER_NAME = "Camunda 8 Orchestration API MCP Server";
  private static final String CLUSTER_SERVER_INSTRUCTIONS =
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
      """;

  private static final String PROCESSES_SERVER_NAME =
      "Camunda 8 Orchestration API Processes MCP Server";
  private static final String PROCESSES_SERVER_INSTRUCTIONS =
      """
      This server exposes Camunda 8 processes as tools. All operations are scoped to the
      permissions of the authenticated user.

      Tools are based on eventually consistent data, i.e., deployed process definitions that are
      configured to be exposed as MCP tools. Recently written data may not appear immediately
      in subsequent reads.

      Invoking a process exposed as tool starts a new process instance of the related process
      definition. The tool returns the internal key of the newly created instance for follow-up
      operations.
      """;

  // ---------------------------------------------------------------------------------------------
  // Transports
  // ---------------------------------------------------------------------------------------------

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
    return webMvcTransport(jsonMapper, CLUSTER_ENDPOINT);
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
  public WebMvcStatelessServerTransport processesTransportProvider(
      @Qualifier("mcpServerJsonMapper") final JsonMapper jsonMapper) {
    return webMvcTransport(jsonMapper, PROCESSES_ENDPOINT);
  }

  // ---------------------------------------------------------------------------------------------
  // Router functions (wire transports into the web stack and apply tenant filters)
  // ---------------------------------------------------------------------------------------------

  /**
   * Dual-path router function for the cluster MCP server.
   *
   * <p>Serves the same transport from two URLs: the default {@code /mcp/cluster} and the
   * physical-tenant-scoped {@code /physical-tenants/{physicalTenantId}/mcp/cluster}.
   *
   * @param clusterTransportProvider the associated transport provider
   */
  @Bean
  public RouterFunction<ServerResponse> clusterRouterFunction(
      @Qualifier("clusterTransportProvider")
          final WebMvcStatelessServerTransport clusterTransportProvider,
      final ObjectProvider<PhysicalTenantIds> tenantIdsProvider) {
    return dualPathRouterFunction(clusterTransportProvider, tenantIdsProvider);
  }

  /**
   * Dual-path router function for the processes MCP server.
   *
   * <p>Serves the same transport from two URLs: the default {@code /mcp/processes} and the
   * physical-tenant-scoped {@code /physical-tenants/{physicalTenantId}/mcp/processes}.
   *
   * @param processesTransportProvider the associated transport provider
   */
  @Bean
  public RouterFunction<ServerResponse> processesRouterFunction(
      @Qualifier("processesTransportProvider")
          final WebMvcStatelessServerTransport processesTransportProvider,
      final ObjectProvider<PhysicalTenantIds> tenantIdsProvider) {
    return dualPathRouterFunction(processesTransportProvider, tenantIdsProvider);
  }

  // ---------------------------------------------------------------------------------------------
  // MCP servers (one bean per transport)
  // ---------------------------------------------------------------------------------------------

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
    return McpServer.sync(clusterTransport)
        .serverInfo(CLUSTER_SERVER_NAME, VersionUtil.getVersion())
        .instructions(CLUSTER_SERVER_INSTRUCTIONS)
        .capabilities(defaultCapabilities())
        .tools(toolSpecifications)
        .immediateExecution(true)
        .validateToolInputs(false) // covered by bean validation
        .build();
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
      @Qualifier("processesToolRepository") final ToolRepository toolRepository,
      @Qualifier("mcpServerJsonMapper") final JsonMapper jsonMapper) {
    final McpStatelessSyncServer server =
        McpServer.sync(processesTransport)
            .serverInfo(PROCESSES_SERVER_NAME, VersionUtil.getVersion())
            .instructions(PROCESSES_SERVER_INSTRUCTIONS)
            .capabilities(defaultCapabilities())
            .immediateExecution(true)
            .validateToolInputs(false) // covered by bean validation
            .build();
    RequestHandlerCustomizer.replaceToolHandlers(processesTransport, jsonMapper, toolRepository);
    return server;
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private static WebMvcStatelessServerTransport webMvcTransport(
      final JsonMapper jsonMapper, final String messageEndpoint) {
    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .messageEndpoint(messageEndpoint)
        .build();
  }

  /**
   * Builds a router function that serves a transport's base endpoint at both the default URL and
   * the physical-tenant-scoped URL.
   *
   * <p>The default branch stamps the {@link PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID
   * default} tenant. The tenant branch nests the same base router under {@link
   * #PHYSICAL_TENANT_URI_PREFIX} so the {@code physicalTenantId} path variable is captured, then
   * validates and stamps it via {@link #tenantFilter}. The transport handler reads only the request
   * body, so the same instance serves both routes.
   */
  private static RouterFunction<ServerResponse> dualPathRouterFunction(
      final WebMvcStatelessServerTransport transport,
      final ObjectProvider<PhysicalTenantIds> tenantIdsProvider) {
    final RouterFunction<ServerResponse> base = transport.getRouterFunction();

    final RouterFunction<ServerResponse> global = base.filter(defaultTenantFilter());
    final RouterFunction<ServerResponse> tenant =
        RouterFunctions.nest(RequestPredicates.path(PHYSICAL_TENANT_URI_PREFIX), base)
            .filter(tenantFilter(tenantIdsProvider));

    return global.and(tenant);
  }

  private static McpSchema.ServerCapabilities defaultCapabilities() {
    return McpSchema.ServerCapabilities.builder()
        .tools(false)
        .resources(false, false)
        .prompts(false)
        .build();
  }

  /**
   * {@link HandlerFilterFunction} that records the {@link
   * PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID default} physical tenant id on the request, so
   * tools can call {@link PhysicalTenantContext#current()} consistently regardless of the URL used.
   */
  static HandlerFilterFunction<ServerResponse, ServerResponse> defaultTenantFilter() {
    return (request, next) -> {
      PhysicalTenantContext.setPhysicalTenantId(
          request.servletRequest(), PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
      return next.handle(request);
    };
  }

  /**
   * {@link HandlerFilterFunction} that reads the {@code physicalTenantId} path variable from the
   * matched route, validates it against the known physical tenants (404 on unknown), and stores the
   * resolved id on the request via {@link PhysicalTenantContext}.
   */
  static HandlerFilterFunction<ServerResponse, ServerResponse> tenantFilter(
      final ObjectProvider<PhysicalTenantIds> tenantIdsProvider) {
    final PhysicalTenantIds tenantIds = tenantIdsProvider.getIfAvailable();
    return (request, next) -> {
      final var knownTenants =
          tenantIds != null
              ? tenantIds.known()
              : Set.of(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
      final String tenantId = request.pathVariable(PATH_VARIABLE_PHYSICAL_TENANT_ID);
      if (!knownTenants.contains(tenantId)) {
        return unknownPhysicalTenantResponse(tenantId);
      }
      PhysicalTenantContext.setPhysicalTenantId(request.servletRequest(), tenantId);
      return next.handle(request);
    };
  }

  private static ServerResponse unknownPhysicalTenantResponse(final String tenantId) {
    return ServerResponse.status(HttpStatus.NOT_FOUND).body("Unknown physical tenant: " + tenantId);
  }
}
