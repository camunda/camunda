/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * MCP-specific incident filter facade record. Declares only the fields exposed to MCP clients with
 * flat Java types (no filter property wrappers). Converted to strict contract types before passing
 * to the mapping layer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpIncidentFilter(
    @JsonProperty
        @JsonPropertyDescription("Date of incident creation.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange creationTime,
    @JsonProperty
        @JsonPropertyDescription("The element ID associated to this incident.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String elementId,
    @JsonProperty
        @JsonPropertyDescription("Incident error type with a defined set of values.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        IncidentErrorTypeEnum errorType,
    @JsonProperty
        @JsonPropertyDescription("The process definition ID associated to this incident.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionId,
    @JsonProperty
        @JsonPropertyDescription("The process definition key associated to this incident.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription("The process instance key associated to this incident.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processInstanceKey,
    @JsonProperty
        @JsonPropertyDescription("State of this incident with a defined set of values.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        IncidentStateEnum state) {}
