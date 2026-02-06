/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.variable;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.TRUNCATE_VARIABLES_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_VALUE_RETURN_FORMAT;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.mcp.model.McpVariableFilter;
import io.camunda.gateway.protocol.model.VariableSearchQuerySortRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class VariableTools {

  private final VariableServices variableServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public VariableTools(
      final VariableServices variableServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.variableServices = variableServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description =
          "Search for variables. " + VARIABLE_VALUE_RETURN_FORMAT + " " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchVariables(
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpVariableFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<VariableSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page,
      @McpToolParam(description = TRUNCATE_VARIABLES_DESCRIPTION, required = false)
          final Boolean truncateValues) {
    try {
      final var variableSearchQuery = SearchQueryRequestMapper.toVariableQuery(filter, page, sort);
      if (variableSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(variableSearchQuery.getLeft());
      }

      final boolean shouldTruncate = truncateValues == null || truncateValues;
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(
              variableServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(variableSearchQuery.get()),
              shouldTruncate));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description =
          "Get variable by key. " + VARIABLE_VALUE_RETURN_FORMAT + " " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getVariable(
      @McpToolParam @Positive(message = VARIABLE_KEY_POSITIVE_MESSAGE) final Long variableKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableItem(
              variableServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(variableKey)));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
