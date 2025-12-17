/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;

public record IncidentSearchQuery(
    @JsonProperty @JsonPropertyDescription("The process definition ID associated to the incident.")
        String processDefinitionId,
    @JsonProperty @JsonPropertyDescription("Incident error type.") IncidentErrorType errorType,
    @JsonProperty
        @JsonPropertyDescription(
            "The element ID associated to the incident - the BPMN element ID in the process.")
        String elementId,
    @JsonProperty
        @JsonPropertyDescription(
            "Date of incident creation - filter from this time (inclusive). "
                + ToolDescriptions.DATE_TIME_FORMAT)
        OffsetDateTime creationTimeFrom,
    @JsonProperty
        @JsonPropertyDescription(
            "Date of incident creation - filter before this time (exclusive). "
                + ToolDescriptions.DATE_TIME_FORMAT)
        OffsetDateTime creationTimeTo,
    @JsonProperty @JsonPropertyDescription("State of the incident.") IncidentState state,
    @JsonProperty
        @JsonPropertyDescription("The process definition key associated to the incident.")
        @Positive
        Long processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription("The process instance key associated to the incident.")
        @Positive
        Long processInstanceKey,
    @JsonProperty @JsonPropertyDescription("Sort criteria") @Valid
        List<@Valid IncidentSearchQuerySortRequest> sort,
    @JsonProperty SearchQueryPageRequest page) {}
