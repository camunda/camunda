/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

public record IncidentSearchQuery(
    @Nullable @JsonPropertyDescription("The process definition ID associated to the incident.")
        String processDefinitionId,
    @Nullable @JsonPropertyDescription("Incident error type.") IncidentErrorType errorType,
    @Nullable @JsonPropertyDescription("Error message.") String errorMessage,
    @Nullable
        @JsonPropertyDescription(
            "The element ID associated to the incident - the BPMN element ID in the process.")
        String elementId,
    @Nullable
        @JsonPropertyDescription(
            "Date of incident creation - filter from this time (inclusive). "
                + ToolDescriptions.DATE_TIME_FORMAT)
        OffsetDateTime creationTimeFrom,
    @Nullable
        @JsonPropertyDescription(
            "Date of incident creation - filter before this time (exclusive). "
                + ToolDescriptions.DATE_TIME_FORMAT)
        OffsetDateTime creationTimeTo,
    @Nullable @JsonPropertyDescription("State of the incident.") IncidentState state,
    @Nullable @JsonPropertyDescription("The tenant ID of the incident.") String tenantId,
    @Nullable
        @JsonPropertyDescription(
            "The assigned key, which acts as a unique identifier for this incident.")
        Long incidentKey,
    @Nullable @JsonPropertyDescription("The process definition key associated to the incident.")
        Long processDefinitionKey,
    @Nullable @JsonPropertyDescription("The process instance key associated to the incident.")
        Long processInstanceKey,
    @Nullable @JsonPropertyDescription("The element instance key associated to the incident.")
        Long elementInstanceKey,
    @Nullable @JsonPropertyDescription("The job key, if exists, associated with the incident.")
        Long jobKey,
    @Nullable @JsonPropertyDescription("Sort criteria") @Valid
        List<@Valid IncidentSearchQuerySortRequest> sort,
    @Nullable @JsonPropertyDescription("Pagination criteria") SearchQueryPageRequest page) {}
