/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.OffsetDateTime;
import java.util.List;

@JsonClassDescription("Search for incidents in the Camunda cluster")
public record IncidentSearchRequest(
    @JsonPropertyDescription("Filter criteria for incidents") IncidentFilter filter,
    @JsonPropertyDescription("Sort criteria for incident results") List<IncidentSort> sort,
    @JsonPropertyDescription("Pagination settings") IncidentPage page) {

  public record IncidentFilter(
      @JsonPropertyDescription("Process instance keys to filter by") List<Long> processInstanceKeys,
      @JsonPropertyDescription("Process definition keys to filter by")
          List<Long> processDefinitionKeys,
      @JsonPropertyDescription("Process definition IDs to filter by")
          List<String> processDefinitionIds,
      @JsonPropertyDescription("Incident keys to filter by") List<Long> incidentKeys,
      @JsonPropertyDescription("Incident states to filter by (ACTIVE, RESOLVED, PENDING, MIGRATED)")
          List<String> states,
      @JsonPropertyDescription("Error types to filter by") List<String> errorTypes,
      @JsonPropertyDescription("Error messages to filter by (partial matches supported)")
          List<String> errorMessages,
      @JsonPropertyDescription("Flow node IDs to filter by") List<String> flowNodeIds,
      @JsonPropertyDescription("Flow node instance keys to filter by")
          List<Long> flowNodeInstanceKeys,
      @JsonPropertyDescription("Creation time range start") OffsetDateTime creationTimeFrom,
      @JsonPropertyDescription("Creation time range end") OffsetDateTime creationTimeTo,
      @JsonPropertyDescription("Job keys to filter by") List<Long> jobKeys,
      @JsonPropertyDescription("Tenant IDs to filter by") List<String> tenantIds) {}

  public record IncidentSort(
      @JsonPropertyDescription(
              "Field to sort by (key, creationTime, state, errorType, processInstanceKey, processDefinitionKey)")
          String field,
      @JsonPropertyDescription("Sort order (asc, desc)") String order) {}

  public record IncidentPage(
      @JsonPropertyDescription("Number of results to return (default: 20, max: 100)") Integer size,
      @JsonPropertyDescription("Search after values for pagination") List<Object> searchAfter) {}
}
