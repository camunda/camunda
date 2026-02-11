/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.dynamic;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.SimpleRequestMapper;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.server.DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification;
import io.camunda.gateway.mcp.server.DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides dynamic MCP tools based on process definitions accessible to the authenticated user.
 *
 * <p>Each process definition becomes an MCP tool that can be invoked to create an instance of that
 * process. Tools are generated on-demand for each "tools/list" request, ensuring the user only sees
 * processes they have permission to access.
 *
 * <p>This is a POJO that should be instantiated as a Spring bean in a configuration class.
 */
public class DynamicProcessToolProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicProcessToolProvider.class);
  private static final String VARIABLES_SCHEMA =
      """
      {
        "type": "object",
        "description": "Variables to set when creating the process instance",
        "additionalProperties": true
      }
      """;

  private final ProcessDefinitionServices processDefinitionServices;
  private final ProcessInstanceServices processInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final McpJsonMapper mcpJsonMapper;

  public DynamicProcessToolProvider(
      final ProcessDefinitionServices processDefinitionServices,
      final ProcessInstanceServices processInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final MultiTenancyConfiguration multiTenancyCfg,
      final McpJsonMapper mcpJsonMapper) {
    this.processDefinitionServices = processDefinitionServices;
    this.processInstanceServices = processInstanceServices;
    this.authenticationProvider = authenticationProvider;
    this.multiTenancyCfg = multiTenancyCfg;
    this.mcpJsonMapper = mcpJsonMapper;
  }

  /**
   * Generates tool specifications dynamically based on process definitions accessible to the
   * current authenticated user.
   *
   * <p>Only includes the latest version of each process definition to avoid duplicate tools for
   * different versions of the same process.
   *
   * @return list of tool specifications, one per accessible process definition (latest version
   *     only)
   */
  public List<AsyncToolSpecification> getToolSpecifications() {
    try {
      // Search for latest versions of process definitions the user has access to
      final ProcessDefinitionQuery query =
          SearchQueryBuilders.processDefinitionSearchQuery(
              b -> b.filter(f -> f.isLatestVersion(true)));

      final var searchResult =
          processDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);

      return searchResult.items().stream()
          .map(this::createToolSpecification)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

    } catch (final Exception e) {
      LOGGER.error("Failed to generate dynamic process tools", e);
      return List.of();
    }
  }

  private AsyncToolSpecification createToolSpecification(
      final ProcessDefinitionEntity processDefinition) {
    try {
      final String toolName = createToolName(processDefinition);
      final String toolDescription = createToolDescription(processDefinition);

      // Set tool annotations following MCP specification defaults
      final var tool =
          McpSchema.Tool.builder()
              .name(toolName)
              .title("Start " + processDefinition.name())
              .description(toolDescription)
              .inputSchema(mcpJsonMapper, VARIABLES_SCHEMA)
              .annotations(new ToolAnnotations(null, null, false, false, null, null))
              .build();

      final BiFunction<McpTransportContext, CallToolRequest, CallToolResult> callHandler =
          (context, request) ->
              handleToolInvocation(processDefinition.processDefinitionKey(), request);

      return AsyncToolSpecification.fromSync(
          SyncToolSpecification.builder().tool(tool).callHandler(callHandler).build());

    } catch (final Exception e) {
      LOGGER.error("Failed to create tool specification for process: {}", processDefinition, e);
      return null;
    }
  }

  private String createToolName(final ProcessDefinitionEntity processDefinition) {
    // Create a tool name from process ID, ensuring it's unique by including version
    // Format: start_processId_v1
    return String.format(
        "start_%s_v%d",
        sanitizeForToolName(processDefinition.processDefinitionId()), processDefinition.version());
  }

  private String sanitizeForToolName(final String input) {
    // Replace any non-alphanumeric characters with underscores to create valid tool names
    return input.replaceAll("[^a-zA-Z0-9]", "_");
  }

  private String createToolDescription(final ProcessDefinitionEntity processDefinition) {
    final StringBuilder description = new StringBuilder();
    description
        .append("Create a new instance of the process '")
        .append(processDefinition.name())
        .append("'");

    if (processDefinition.processDefinitionId() != null) {
      description
          .append(" (Process ID: ")
          .append(processDefinition.processDefinitionId())
          .append(")");
    }

    description
        .append(". Version: ")
        .append(processDefinition.version())
        .append(". Process Definition Key: ")
        .append(processDefinition.processDefinitionKey())
        .append(".");

    return description.toString();
  }

  private CallToolResult handleToolInvocation(
      final Long processDefinitionKey, final CallToolRequest request) {
    try {
      // Extract variables from the request arguments
      final Map<String, Object> variables = extractVariables(request);

      // Create the process instance
      final var instruction =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey(String.valueOf(processDefinitionKey))
              .awaitCompletion(false);

      if (variables != null && !variables.isEmpty()) {
        instruction.variables(variables);
      }

      final var createRequest =
          SimpleRequestMapper.toCreateProcessInstance(
              instruction, multiTenancyCfg.isChecksEnabled());

      if (createRequest.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(createRequest.getLeft());
      }

      final var result =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .createProcessInstance(createRequest.get());

      return CallToolResultMapper.from(result, ResponseMapper::toCreateProcessInstanceResponse);

    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractVariables(final CallToolRequest request) {
    try {
      final var arguments = request.arguments();
      if (arguments != null && arguments instanceof Map) {
        return (Map<String, Object>) arguments;
      }
      return Map.of();
    } catch (final Exception e) {
      LOGGER.warn("Failed to extract variables from request", e);
      return Map.of();
    }
  }
}
