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

  private static final Logger logger = LoggerFactory.getLogger(GatewayMcpConfiguration.class);

  @Bean
  ToolCallback incidentSearchToolCallback(final ClusterIncidentsTool clusterIncidentsTool) {
    return FunctionToolCallback.builder("searchIncidents", clusterIncidentsTool::searchIncidents)
        .description("Search for incidents in the Camunda cluster")
        .inputType(IncidentSearchRequest.class)
        .build();
  }

  @Bean
  public ToolCallbackProvider tools(List<ToolCallback> toolCallbacks) {
    return new StaticToolCallbackProvider(toolCallbacks);
  }

  /** Server configuration for Model Context Protocol (MCP) using Web MVC. */
  @Bean
  public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
      ObjectProvider<ObjectMapper> objectMapperProvider) {
    ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    return new WebMvcSseServerTransportProvider(objectMapper, "", "/mcp/message", "/sse");
  }

  @Bean
  public RouterFunction<ServerResponse> mvcMcpRouterFunction(
      WebMvcSseServerTransportProvider transportProvider) {
    return transportProvider.getRouterFunction();
  }

  @Bean
  public McpSyncServer mcpSyncServer(
      GatewayMcpProperties serverProperties,
      McpServerTransportProvider transportProvider,
      List<ToolCallbackProvider> toolCallbackProvider) {
    // Create the server
    Implementation serverInfo =
        new Implementation(serverProperties.getServerName(), serverProperties.getVersion());
    SyncSpecification serverBuilder = McpServer.sync(transportProvider).serverInfo(serverInfo);

    // Add tools available
    List<ToolCallback> providerToolCallbacks =
        toolCallbackProvider.stream()
            .map(pr -> List.of(pr.getToolCallbacks()))
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .toList();

    var toolSpecifications = this.toSyncToolSpecifications(providerToolCallbacks);

    if (!CollectionUtils.isEmpty(toolSpecifications)) {
      serverBuilder.tools(toolSpecifications);
      logger.info("Registered tools: {}", toolSpecifications.size());
    }

    serverBuilder.capabilities(ServerCapabilities.builder().tools(false).build());
    serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

    return serverBuilder.build();
  }

  private List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(
      List<ToolCallback> tools) {
    return tools.stream().map(McpToolUtils::toSyncToolSpecification).toList();
  }
}
