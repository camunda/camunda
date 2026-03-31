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
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.gateway.protocol.model.simple.VariableValueFilterProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Set;

/**
 * MCP-specific process instance filter facade record. Declares only the fields exposed to MCP
 * clients with flat Java types. Converted to strict contract types before passing to the mapping
 * layer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpProcessInstanceFilter(
    @JsonProperty
        @JsonPropertyDescription("Filter by start date of the process instance.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange startDate,
    @JsonProperty
        @JsonPropertyDescription("Filter by end date of the process instance.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange endDate,
    @JsonProperty
        @JsonPropertyDescription("Filter by process instance state.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        ProcessInstanceStateEnum state,
    @JsonProperty
        @JsonPropertyDescription("Filter by whether the process instance has an incident.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Boolean hasIncident,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition ID (BPMN process ID).")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionId,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition name.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionName,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition version.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Integer processDefinitionVersion,
    @JsonProperty
        @JsonPropertyDescription("Filter by process instance key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processInstanceKey,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by variable values. Each item has a 'name' and 'value' field.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        List<VariableValueFilterProperty> variables,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by tags. Tags must start with a letter, followed by alphanumerics, `_`, `-`, `:`, or `.`; max length 100.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Set<String> tags,
    @JsonProperty
        @JsonPropertyDescription("Filter by business ID.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String businessId) {}
