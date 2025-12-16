/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.lang.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "IncidentFilter", description = "Incident search filter.")
public record IncidentFilter(
    @Nullable
        @Valid
        @Schema(
            name = "processDefinitionId",
            description = "The process definition ID associated to this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter processDefinitionId,
    @Nullable
        @Valid
        @Schema(
            name = "errorType",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        IncidentErrorTypeFilter errorType,
    @Nullable
        @Valid
        @Schema(
            name = "errorMessage",
            description = "Error message filter.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter errorMessage,
    @Nullable
        @Valid
        @Schema(
            name = "elementId",
            description = "The element ID associated to this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter elementId,
    @Nullable
        @Valid
        @Schema(
            name = "creationTime",
            description = "Date of incident creation.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        DateTimeFilter creationTime,
    @Nullable
        @Valid
        @Schema(
            name = "state",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        IncidentStateFilter state,
    @Nullable
        @Valid
        @Schema(
            name = "tenantId",
            description = "The tenant ID of the incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter tenantId,
    @Schema(
            name = "incidentKey",
            description = "The assigned key, which acts as a unique identifier for this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter incidentKey,
    @Schema(
            name = "processDefinitionKey",
            description = "The process definition key associated to this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter processDefinitionKey,
    @Schema(
            name = "processInstanceKey",
            description = "The process instance key associated to this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter processInstanceKey,
    @Schema(
            name = "elementInstanceKey",
            description = "The element instance key associated to this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter elementInstanceKey,
    @Schema(
            name = "jobKey",
            description = "The job key, if exists, associated with this incident.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StringFilter jobKey) {}
