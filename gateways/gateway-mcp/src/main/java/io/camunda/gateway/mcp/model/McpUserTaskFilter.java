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
import io.camunda.gateway.protocol.model.UserTaskStateEnum;
import io.camunda.gateway.protocol.model.simple.VariableValueFilterProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Set;

/**
 * MCP-specific user task filter facade record. Declares only the fields exposed to MCP clients with
 * flat Java types. Converted to strict contract types before passing to the mapping layer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpUserTaskFilter(
    @JsonProperty
        @JsonPropertyDescription("Filter by user task state.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        UserTaskStateEnum state,
    @JsonProperty
        @JsonPropertyDescription("Filter by assignee.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String assignee,
    @JsonProperty
        @JsonPropertyDescription("Filter by priority.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Integer priority,
    @JsonProperty
        @JsonPropertyDescription("Filter by element ID.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String elementId,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by user task name. Only matches data created with Camunda 8.8 and later.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String name,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition ID.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionId,
    @JsonProperty
        @JsonPropertyDescription("Filter by creation date.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange creationDate,
    @JsonProperty
        @JsonPropertyDescription("Filter by completion date.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange completionDate,
    @JsonProperty
        @JsonPropertyDescription("Filter by follow-up date.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange followUpDate,
    @JsonProperty
        @JsonPropertyDescription("Filter by due date.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpDateRange dueDate,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by process instance variables. Each item has a 'name' and 'value' field.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        List<VariableValueFilterProperty> processInstanceVariables,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by local variables. Each item has a 'name' and 'value' field.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        List<VariableValueFilterProperty> localVariables,
    @JsonProperty
        @JsonPropertyDescription("Filter by user task key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String userTaskKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by process instance key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processInstanceKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by element instance key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String elementInstanceKey,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by tags. Tags must start with a letter, followed by alphanumerics, `_`, `-`, `:`, or `.`; max length 100.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Set<String> tags) {}
