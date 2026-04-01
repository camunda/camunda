/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.variable;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_VALUE_RETURN_FORMAT;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableKeyFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpVariableFilter;
import io.camunda.gateway.mcp.model.McpVariableSearchQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
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
      @McpToolParamsUnwrapped @Valid final McpVariableSearchQuery query) {
    try {
      final var strictRequest = toStrictContract(query);
      final var variableSearchQuery = SearchQueryRequestMapper.toVariableQueryStrict(strictRequest);
      if (variableSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(variableSearchQuery.getLeft());
      }

      final boolean shouldTruncate = query.truncateValues() == null || query.truncateValues();
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(
              variableServices.search(
                  variableSearchQuery.get(), authenticationProvider.getCamundaAuthentication()),
              shouldTruncate));
    } catch (final IllegalArgumentException e) {
      return CallToolResultMapper.mapProblemToResult(
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT"));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description =
          "Get variable by key. " + VARIABLE_VALUE_RETURN_FORMAT + " " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getVariable(
      @McpToolParam(description = VARIABLE_KEY_DESCRIPTION)
          @NotNull(message = VARIABLE_KEY_NOT_NULL_MESSAGE)
          @Positive(message = VARIABLE_KEY_POSITIVE_MESSAGE)
          final Long variableKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableItem(
              variableServices.getByKey(
                  variableKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  // -- Facade → Strict contract conversion --

  private static GeneratedVariableSearchQueryRequestStrictContract toStrictContract(
      final McpVariableSearchQuery query) {
    return new GeneratedVariableSearchQueryRequestStrictContract(
        query.page(), query.sort(), toStrictFilter(query.filter()));
  }

  private static GeneratedVariableFilterStrictContract toStrictFilter(
      final McpVariableFilter filter) {
    if (filter == null) {
      return null;
    }
    return new GeneratedVariableFilterStrictContract(
        wrapString(filter.name()),
        wrapString(filter.value()),
        null, // tenantId — not exposed in MCP
        filter.isTruncated(),
        filter.variableKey() != null
            ? new GeneratedVariableKeyFilterPropertyPlainValueStrictContract(filter.variableKey())
            : null,
        filter.scopeKey() != null
            ? new io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedScopeKeyFilterPropertyPlainValueStrictContract(filter.scopeKey())
            : null,
        filter.processInstanceKey() != null
            ? new io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedProcessInstanceKeyFilterPropertyPlainValueStrictContract(
                filter.processInstanceKey())
            : null);
  }

  private static GeneratedStringFilterPropertyStrictContract wrapString(final String value) {
    return value != null ? new GeneratedStringFilterPropertyPlainValueStrictContract(value) : null;
  }
}
