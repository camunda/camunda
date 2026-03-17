/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.CamundaMcpToolScannerAutoConfiguration.CamundaMcpToolAnnotatedBeans;
import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.CamundaSyncStatelessMcpToolProvider;
import io.camunda.gateway.mcp.model.McpIncidentFilter;
import io.camunda.gateway.mcp.model.McpProcessDefinitionFilter;
import io.camunda.gateway.mcp.model.McpProcessInstanceCreationInstruction;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentRequest;
import io.camunda.gateway.mcp.model.McpUserTaskFilter;
import io.camunda.gateway.mcp.model.McpVariableFilter;
import io.camunda.gateway.protocol.model.simple.IncidentFilter;
import io.camunda.gateway.protocol.model.simple.ProcessDefinitionFilter;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.simple.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.simple.UserTaskFilter;
import io.camunda.gateway.protocol.model.simple.VariableFilter;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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

  @Bean
  @ConditionalOnMissingBean
  public CamundaJsonSchemaGenerator mcpGatewayJsonSchemaGenerator(final ObjectMapper objectMapper) {
    return new CamundaJsonSchemaGenerator(objectMapper);
  }

  @Bean("gatewayMcpObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> gatewayMcpObjectMapperCustomizer() {
    final var module =
        new SimpleModule("gateway-mcp-module")
            .setMixInAnnotation(IncidentFilter.class, McpIncidentFilter.class)
            .setMixInAnnotation(ProcessDefinitionFilter.class, McpProcessDefinitionFilter.class)
            .setMixInAnnotation(
                ProcessInstanceCreationInstruction.class,
                McpProcessInstanceCreationInstruction.class)
            .setMixInAnnotation(ProcessInstanceFilter.class, McpProcessInstanceFilter.class)
            .setMixInAnnotation(UserTaskAssignmentRequest.class, McpUserTaskAssignmentRequest.class)
            .setMixInAnnotation(UserTaskFilter.class, McpUserTaskFilter.class)
            .setMixInAnnotation(VariableFilter.class, McpVariableFilter.class);
    return builder -> builder.modulesToInstall(modules -> modules.add(module));
  }

  @Bean
  public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
    return builder ->
        builder
            .addMixIn(IncidentFilter.class, McpIncidentFilter.class)
            .addMixIn(ProcessDefinitionFilter.class, McpProcessDefinitionFilter.class)
            .addMixIn(
                ProcessInstanceCreationInstruction.class,
                McpProcessInstanceCreationInstruction.class)
            .addMixIn(ProcessInstanceFilter.class, McpProcessInstanceFilter.class)
            .addMixIn(UserTaskAssignmentRequest.class, McpUserTaskAssignmentRequest.class)
            .addMixIn(UserTaskFilter.class, McpUserTaskFilter.class)
            .addMixIn(VariableFilter.class, McpVariableFilter.class);
  }

  @Bean
  public List<SyncToolSpecification> mcpGatewayToolSpecifications(
      final CamundaMcpToolAnnotatedBeans annotatedBeans,
      final CamundaJsonSchemaGenerator jsonSchemaGenerator) {
    return new CamundaSyncStatelessMcpToolProvider(
            annotatedBeans.getBeansByAnnotation(CamundaMcpTool.class), jsonSchemaGenerator)
        .getToolSpecifications();
  }
}
