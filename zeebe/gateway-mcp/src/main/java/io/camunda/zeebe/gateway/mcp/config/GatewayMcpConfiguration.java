/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.mcp.tools.incident.ClusterIncidentsTool;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.mcp.tools")
@EnableConfigurationProperties(GatewayMcpProperties.class)
public class GatewayMcpConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(GatewayMcpConfiguration.class);

  @Bean
  ToolCallback incidentSearchToolCallback(final ClusterIncidentsTool clusterIncidentsTool) {
    return FunctionToolCallback.builder("searchIncidents", clusterIncidentsTool::searchIncidents)
        .description("Search for incidents in the Camunda cluster")
        .inputType(IncidentSearchRequest.class)
        .build();
  }

  @Bean
  public ToolCallbackProvider tools(final List<ToolCallback> toolCallbacks) {
    return new StaticToolCallbackProvider(toolCallbacks);
  }

  /** Server configuration for Model Context Protocol (MCP) using Web MVC. */
  @Bean
  public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
      final ObjectProvider<ObjectMapper> objectMapperProvider) {
    final ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    return WebMvcSseServerTransportProvider.builder()
        .sseEndpoint("/sse")
        .messageEndpoint("/mcp/message")
        .baseUrl("")
        .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> mvcMcpRouterFunction(
      final WebMvcSseServerTransportProvider transportProvider) {
    return transportProvider.getRouterFunction();
  }

  @Bean
  public McpSyncServer mcpSyncServer(
      final GatewayMcpProperties serverProperties,
      final McpServerTransportProvider transportProvider,
      final List<ToolCallbackProvider> toolCallbackProvider) {
    // Create the server
    final Implementation serverInfo =
        new Implementation(serverProperties.getServerName(), serverProperties.getVersion());
    final SyncSpecification serverBuilder =
        McpServer.sync(transportProvider).serverInfo(serverInfo);

    // Add tools available
    final List<ToolCallback> providerToolCallbacks =
        toolCallbackProvider.stream()
            .map(pr -> List.of(pr.getToolCallbacks()))
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .toList();

    final var toolSpecifications = toSyncToolSpecifications(providerToolCallbacks);

    if (!CollectionUtils.isEmpty(toolSpecifications)) {
      serverBuilder.tools(toolSpecifications);
      LOGGER.info("Registered tools: {}", toolSpecifications.size());
    }

    serverBuilder.capabilities(ServerCapabilities.builder().tools(false).build());
    serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

    return serverBuilder.build();
  }

  private List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(
      final List<ToolCallback> tools) {
    return tools.stream().map(McpToolUtils::toSyncToolSpecification).toList();
  }
}
