/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.definition;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpProcessDefinitionFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
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
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpProcessDefinitionFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<ProcessDefinitionSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page) {
    try {
      final var query = SearchQueryRequestMapper.toProcessDefinitionQuery(filter, page, sort);
      if (query.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(query.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(
              processDefinitionServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(query.get())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinition(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinition(
              processDefinitionServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(processDefinitionKey)));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get the BPMN XML of a process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinitionXml(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      final var xml =
          processDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getProcessDefinitionXml(processDefinitionKey)
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
