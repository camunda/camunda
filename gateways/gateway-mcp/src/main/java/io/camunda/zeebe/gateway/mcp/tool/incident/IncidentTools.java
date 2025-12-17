/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper.mapErrorToResult;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryFilterMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryPageMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQuerySortRequestMapper;
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchQuery;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public CallToolResult searchIncidents(@Valid final IncidentSearchQuery request) {
    try {
      final var sortRequest = SearchQuerySortRequestMapper.toIncidentSearchSort(request.sort());
      if (sortRequest.isLeft()) {
        return sortRequest.mapLeft(CallToolResultMapper::mapViolationsToResult).getLeft();
      }

      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(
                  SearchQueryBuilders.incidentSearchQuery()
                      .filter(SearchQueryFilterMapper.toIncidentFilter(request))
                      .page(SearchQueryPageMapper.toSearchQueryPage(request.page()))
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
