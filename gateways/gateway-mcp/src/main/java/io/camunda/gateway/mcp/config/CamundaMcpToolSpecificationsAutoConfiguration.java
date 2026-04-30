/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.CamundaMcpToolScannerAutoConfiguration.CamundaMcpToolAnnotatedBeans;
import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.CamundaSyncStatelessMcpToolProvider;
import io.camunda.gateway.mcp.tool.process.state.ProcessStateTools;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration for creating MCP tool specifications from {@link CamundaMcpTool}-annotated
 * methods.
 *
 * <p>This configuration runs after {@link CamundaMcpToolScannerAutoConfiguration} which populates
 * the {@link CamundaMcpToolAnnotatedBeans} registry with discovered tool beans.
 */
@AutoConfiguration(after = CamundaMcpToolScannerAutoConfiguration.class)
@ConditionalOnMcpGatewayEnabled
public class CamundaMcpToolSpecificationsAutoConfiguration {

  /**
   * Creates the JSON schema generator using Spring AI's {@link JsonParser#getJsonMapper()}.
   *
   * <p>This Jackson 3 mapper already has MCP mixins registered via SPI ({@link
   * CamundaMcpJackson3Module}), so no additional mixin configuration is needed.
   */
  @Bean
  @ConditionalOnMissingBean
  public CamundaJsonSchemaGenerator mcpGatewayJsonSchemaGenerator() {
    return new CamundaJsonSchemaGenerator(JsonParser.getJsonMapper());
  }

  @Bean(name = "clusterMcpToolSpecifications")
  public List<SyncToolSpecification> mcpGatewayToolSpecifications(
      final CamundaMcpToolAnnotatedBeans annotatedBeans,
      final CamundaJsonSchemaGenerator jsonSchemaGenerator) {
    return new CamundaSyncStatelessMcpToolProvider(
            annotatedBeans.getBeansByAnnotation(CamundaMcpTool.class), jsonSchemaGenerator)
        .getToolSpecifications();
  }

  /**
   * Creates static tool specifications for the processes MCP server.
   *
   * <p>The processes server exposes a fixed set of helper tools alongside dynamic process
   * definition tools. These static tools are always available regardless of which process
   * definitions are deployed.
   */
  @Bean(name = "processesMcpStaticToolSpecifications")
  public List<SyncToolSpecification> processesMcpStaticToolSpecifications(
      final ObjectProvider<ProcessStateTools> processStateToolsProvider,
      final CamundaJsonSchemaGenerator jsonSchemaGenerator) {
    final ProcessStateTools processStateTools = processStateToolsProvider.getIfAvailable();
    if (processStateTools == null) {
      return List.of();
    }
    return new CamundaSyncStatelessMcpToolProvider(List.of(processStateTools), jsonSchemaGenerator)
        .getToolSpecifications();
  }

  /**
   * Default processes tool repository backed by the static tool specifications.
   *
   * <p>This default is replaced when a real implementation (e.g. one that also exposes deployed
   * process definitions as tools) is present. Any overriding bean should inject {@code
   * processesMcpStaticToolSpecifications} and include those specs to keep the static tools
   * available.
   */
  @Bean(name = "processesToolRepository")
  @ConditionalOnMissingBean(name = "processesToolRepository")
  public ToolRepository processesToolRepository(
      @Qualifier("processesMcpStaticToolSpecifications")
          final List<SyncToolSpecification> staticToolSpecs) {
    final Map<String, SyncToolSpecification> toolMap =
        staticToolSpecs.stream()
            .collect(Collectors.toMap(spec -> spec.tool().name(), spec -> spec));
    return new ToolRepository() {
      @Override
      public @NonNull List<Tool> getTools(@NonNull final McpTransportContext transportContext) {
        return toolMap.values().stream().map(SyncToolSpecification::tool).toList();
      }

      @Override
      public @NonNull Either<String, SyncToolSpecification> findTool(
          @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
        final var spec = toolMap.get(toolName);
        return spec != null ? Either.right(spec) : Either.left("Tool not found: " + toolName);
      }
    };
  }
}
