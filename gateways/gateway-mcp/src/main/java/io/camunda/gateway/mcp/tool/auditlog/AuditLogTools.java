/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.auditlog;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpAuditLogFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.AuditLogSearchQuerySortRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuditLogServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class AuditLogTools {

  private final AuditLogServices auditLogServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AuditLogTools(
      final AuditLogServices auditLogServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.auditLogServices = auditLogServices;
    this.authenticationProvider = authenticationProvider;
  }

  @McpTool(
      description = "Search for audit log entries. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchAuditLogs(
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpAuditLogFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<AuditLogSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page) {
    try {
      final var query = SearchQueryRequestMapper.toAuditLogQuery(filter, page, sort);
      if (query.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(query.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toAuditLogSearchQueryResponse(
              auditLogServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(query.get())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
