/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class IncidentTools {
  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public IncidentTools(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  //  @McpTool(
  //      description = "Search for incidents based on given criteria. " +
  // EVENTUAL_CONSISTENCY_NOTE,
  //      annotations = @McpAnnotations(readOnlyHint = true))
  //  public CallToolResult searchIncidents(
  //      @McpToolParam(required = false) final IncidentFilter filter,
  //      @McpToolParam(description = "Sort criteria", required = false) @Valid
  //          final List<@Valid IncidentSearchQuerySortRequest> sort,
  //      @McpToolParam(description = "Pagination criteria", required = false)
  //          final SearchQueryPageRequest page) {
  //
  //    final var query = new IncidentSearchQuery();
  //    query.setFilter(filter);
  //    query.setSort(sort);
  //    query.setPage(page);
  //
  //    return SearchQueryRequestMapper.toIncidentQuery(query)
  //        .fold(CallToolResultMapper::mapProblemToResult, this::searchIncidents);
  //  }
  //
  //  private CallToolResult searchIncidents(final IncidentQuery query) {
  //    try {
  //      final var result =
  //          incidentServices
  //              .withAuthentication(authenticationProvider.getCamundaAuthentication())
  //              .search(query);
  //      return CallToolResultMapper.from(
  //          SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
  //    } catch (final ValidationException e) {
  //      final var problemDetail =
  //          RestErrorMapper.createProblemDetail(
  //              HttpStatus.BAD_REQUEST,
  //              e.getMessage(),
  //              "Validation failed for Incident Search Query");
  //      return mapProblemToResult(problemDetail);
  //    } catch (final Exception e) {
  //      return mapErrorToResult(e);
  //    }
  //  }
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
