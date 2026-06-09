/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.message;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.gateway.mapping.http.physicaltenants.PhysicalTenantContext;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.protocol.model.simple.MessageSubscriptionFilter;
import io.camunda.gateway.protocol.model.simple.MessageSubscriptionSearchQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class MessageSubscriptionTools {

  private static final int PARTITION_1 = 1;

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public MessageSubscriptionTools(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description =
          "Search for message subscriptions restricted to partition 1. "
              + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchMessageSubscriptions(
      @McpToolParamsUnwrapped @Valid final MessageSubscriptionSearchQuery query) {
    try {
      // Always restrict to partition 1 — MCP clients should only access single-partition data.
      final var filter =
          query.getFilter() != null ? query.getFilter() : new MessageSubscriptionFilter();
      filter.setPartitionId(PARTITION_1);
      query.setFilter(filter);

      final var messageSubscriptionQuery =
          SearchQueryRequestMapper.toMessageSubscriptionQuery(query);
      if (messageSubscriptionQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(messageSubscriptionQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(
              serviceRegistry
                  .messageSubscriptionServices(PhysicalTenantContext.current())
                  .search(
                      messageSubscriptionQuery.get(),
                      authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
