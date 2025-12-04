/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.zeebe.gateway.protocol.rest.IncidentFilter;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResolutionRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQuerySortRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.rest.controller.IncidentController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class IncidentTools {
  private final IncidentController incidentController;

  public IncidentTools(final IncidentController incidentController) {
    this.incidentController = incidentController;
  }

  @McpTool(
      description = "Search for incidents based on given criteria. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @McpToolParam(required = false) final IncidentFilter filter,
      @McpToolParam(description = "Sort criteria", required = false) @Valid
          final List<@Valid IncidentSearchQuerySortRequest> sort,
      @McpToolParam(description = "Pagination criteria", required = false)
          final SearchQueryPageRequest page) {

    final var query = new IncidentSearchQuery();
    query.setFilter(filter);
    query.setSort(sort);
    query.setPage(page);

    return incidentController.searchIncidents(query);
  }

  @McpTool(
      description = "Get incident by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public ResponseEntity<IncidentResult> getIncident(
      @McpToolParam(
              description =
                  "The assigned key of the incident, which acts as a unique identifier for this incident.")
          @Positive
          final Long incidentKey) {
    return incidentController.getByKey(incidentKey);
  }

  @McpTool(description = "Resolve incident")
  public void resolveIncident(
      @McpToolParam(description = "Key of the incident to resolve.") @Positive
          final Long incidentKey,
      final IncidentResolutionRequest resolutionRequest) {
    incidentController.incidentResolution(incidentKey, resolutionRequest);
  }
}
