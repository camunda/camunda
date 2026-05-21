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
import io.camunda.gateway.mcp.processes.ProcessesToolRepository;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageSubscriptionServices;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.List;
import org.springframework.ai.util.json.JsonParser;
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

  @Bean(name = "processesMcpToolSpecifications")
  public List<SyncToolSpecification> processesMcpToolSpecifications(
      final CamundaMcpToolAnnotatedBeans annotatedBeans,
      final CamundaJsonSchemaGenerator jsonSchemaGenerator) {
    return new CamundaSyncStatelessMcpToolProvider(
            annotatedBeans.getBeansByAnnotation(CamundaMcpTool.class), jsonSchemaGenerator)
        .getToolSpecifications(CamundaMcpTool::processesServer);
  }

  @Bean(name = "processesToolRepository")
  @ConditionalOnMissingBean(name = "processesToolRepository")
  public ToolRepository processesToolRepository(
      final MessageSubscriptionServices messageSubscriptionServices,
      final MessageServices messageServices,
      final CamundaAuthenticationProvider authenticationProvider,
      @Qualifier("processesMcpToolSpecifications") final List<SyncToolSpecification> staticTools) {
    return new ProcessesToolRepository(
        messageSubscriptionServices, messageServices, authenticationProvider, staticTools);
  }
}
