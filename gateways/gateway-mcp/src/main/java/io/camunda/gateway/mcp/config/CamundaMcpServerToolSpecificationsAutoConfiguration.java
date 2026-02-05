/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration.ToolCallbackConverterCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration(after = McpServerAnnotationScannerAutoConfiguration.class)
@Conditional({EnabledStatelessServerCondition.class, ToolCallbackConverterCondition.class})
@ConditionalOnMcpGatewayEnabled
public class CamundaMcpServerToolSpecificationsAutoConfiguration {

  @Bean
  public List<SyncToolSpecification> mcpGatewayToolSpecifications(
      final ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
    final List<Object> beansByAnnotation =
        beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class);
    // Use Camunda's custom provider with CamundaJsonSchemaGenerator
    final List<McpStatelessServerFeatures.SyncToolSpecification> syncToolSpecifications =
        new CamundaSyncStatelessMcpToolProvider(beansByAnnotation).getToolSpecifications();
    return syncToolSpecifications;
  }
}
