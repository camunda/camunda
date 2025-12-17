/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper.mapErrorToResult;
import static io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryFilterMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryPageMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQuerySortRequestMapper;
import io.camunda.zeebe.gateway.mcp.model.IncidentErrorType;
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchQuerySortRequest;
import io.camunda.zeebe.gateway.mcp.model.IncidentState;
import io.camunda.zeebe.gateway.mcp.model.SearchQueryPageRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class IncidentTools {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentTools.class);

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public IncidentTools(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  @McpTool(
      description = "Search for incidents based on given criteria. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchIncidents(
      @McpToolParam(
              description = "The process definition ID associated to the incident.",
              required = false)
          final String processDefinitionId,
      @McpToolParam(description = "Incident error type.", required = false)
          final IncidentErrorType errorType,
      @McpToolParam(description = "Error message.", required = false) final String errorMessage,
      @McpToolParam(
              description =
                  "The element ID associated to the incident - the BPMN element ID in the process.",
              required = false)
          final String elementId,
      @McpToolParam(
              description =
                  "Date of incident creation - filter from this time (inclusive). RFC 3339 format (e.g., '2024-12-17T10:30:00Z' or '2024-12-17T10:30:00+01:00').",
              required = false)
          final OffsetDateTime creationTimeFrom,
      @McpToolParam(
              description =
                  "Date of incident creation - filter before this time (exclusive). RFC 3339 format (e.g., '2024-12-17T23:59:59Z' or '2024-12-17T23:59:59-05:00').",
              required = false)
          final OffsetDateTime creationTimeTo,
      @McpToolParam(description = "State of the incident.", required = false)
          final IncidentState state,
      @McpToolParam(description = "The tenant ID of the incident.", required = false)
          final String tenantId,
      @McpToolParam(
              description =
                  "The assigned key, which acts as a unique identifier for this incident.",
              required = false)
          final Long incidentKey,
      @McpToolParam(
              description = "The process definition key associated to the incident.",
              required = false)
          final Long processDefinitionKey,
      @McpToolParam(
              description = "The process instance key associated to the incident.",
              required = false)
          final Long processInstanceKey,
      @McpToolParam(
              description = "The element instance key associated to the incident.",
              required = false)
          final Long elementInstanceKey,
      @McpToolParam(
              description = "The job key, if exists, associated with the incident.",
              required = false)
          final Long jobKey,
      @McpToolParam(description = "Sort criteria", required = false) @Valid
          final List<@Valid IncidentSearchQuerySortRequest> sort,
      @McpToolParam(description = "Pagination criteria", required = false)
          final SearchQueryPageRequest page) {

    try {
      final var sortRequest = SearchQuerySortRequestMapper.toIncidentSearchSort(sort);
      if (sortRequest.isLeft()) {
        return sortRequest.mapLeft(CallToolResultMapper::mapViolationsToResult).getLeft();
      }

      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(
                  SearchQueryBuilders.incidentSearchQuery()
                      .filter(
                          SearchQueryFilterMapper.toIncidentFilter(
                              processDefinitionId,
                              errorType,
                              errorMessage,
                              elementId,
                              creationTimeFrom,
                              creationTimeTo,
                              state,
                              tenantId,
                              incidentKey,
                              processDefinitionKey,
                              processInstanceKey,
                              elementInstanceKey,
                              jobKey))
                      .page(SearchQueryPageMapper.toSearchQueryPage(page))
                      .sort(sortRequest.get())
                      .build());

      return CallToolResultMapper.from(result);
    } catch (final Exception e) {
      return mapErrorToResult(e);
    }
  }

  //
  //  @McpTool(
  //      description = "Get incident by key. " + EVENTUAL_CONSISTENCY_NOTE,
  //      annotations = @McpAnnotations(readOnlyHint = true))
  //  public CallToolResult getIncident(
  //      @McpToolParam(
  //              description =
  //                  "The assigned key of the incident, which acts as a unique identifier for this
  // incident.")
  //          @Positive
  //          final Long incidentKey) {
  //    try {
  //      return CallToolResultMapper.from(
  //          SearchQueryResponseMapper.toIncident(
  //              incidentServices
  //                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
  //                  .getByKey(incidentKey)));
  //    } catch (final Exception e) {
  //      return mapErrorToResult(e);
  //    }
  //  }
  //
  //  @McpTool(description = "Resolve incident")
  //  public CallToolResult resolveIncident(
  //      @McpToolParam(description = "Key of the incident to resolve.") @Positive
  //          final Long incidentKey,
  //      final IncidentResolutionRequest incidentResolutionRequest) {
  //    final Long operationReference =
  //        incidentResolutionRequest == null
  //            ? null
  //            : incidentResolutionRequest.getOperationReference();
  //
  //    try {
  //      incidentServices
  //          .withAuthentication(authenticationProvider.getCamundaAuthentication())
  //          .resolveIncident(incidentKey, operationReference);
  //
  //      return CallToolResult.builder().build();
  //    } catch (final Exception e) {
  //      return mapErrorToResult(e);
  //    }
  //  }
}
