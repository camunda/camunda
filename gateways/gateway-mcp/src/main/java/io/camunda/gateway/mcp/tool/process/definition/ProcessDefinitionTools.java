/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.definition;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.context.PhysicalTenantContext;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.protocol.model.simple.ProcessDefinitionSearchQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ProcessDefinitionTools {

  private final ProcessDefinitionServices processDefinitionServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessDefinitionTools(
      final ProcessDefinitionServices processDefinitionServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processDefinitionServices = processDefinitionServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description = "Search for process definitions. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchProcessDefinitions(
      @McpToolParamsUnwrapped @Valid final ProcessDefinitionSearchQuery query) {
    try {
      final var processDefinitionQuery = SearchQueryRequestMapper.toProcessDefinitionQuery(query);
      if (processDefinitionQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(processDefinitionQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(
              processDefinitionServices.search(
                  processDefinitionQuery.get(),
                  authenticationProvider.getCamundaAuthentication(),
                  PhysicalTenantContext.current())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinition(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @NotNull(message = PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinition(
              processDefinitionServices.getByKey(
                  processDefinitionKey,
                  authenticationProvider.getCamundaAuthentication(),
                  PhysicalTenantContext.current())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get the BPMN XML of a process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinitionXml(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @NotNull(message = PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      final var xml =
          processDefinitionServices
              .getProcessDefinitionXml(
                  processDefinitionKey,
                  authenticationProvider.getCamundaAuthentication(),
                  PhysicalTenantContext.current())
              .orElseThrow(
                  () ->
                      new ServiceException(
                          "The BPMN XML for this process definition is not available.",
                          Status.NOT_FOUND));

      return CallToolResult.builder().addTextContent(xml).build();
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
