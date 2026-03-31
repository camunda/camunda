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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * MCP-specific variable filter facade record. Declares only the fields exposed to MCP clients with
 * flat Java types (no filter property wrappers). Converted to strict contract types before passing
 * to the mapping layer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpVariableFilter(
    @JsonProperty
        @JsonPropertyDescription("Filter by variable name.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String name,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by variable value. Values must be in serialized JSON format (e.g., `\"myValue\"` for string value `myValue`). Escape special JSON characters appropriately.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String value,
    @JsonProperty
        @JsonPropertyDescription("Filter by whether the value is truncated.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Boolean isTruncated,
    @JsonProperty
        @JsonPropertyDescription("Filter by variable key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String variableKey,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by scope key. The scope key identifies where a variable is directly defined: a process instance key for process-level variables, or an element instance key for local variables (tasks, subprocesses, etc.). Does not match variables inherited from parent scopes.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String scopeKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by process instance key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processInstanceKey) {}
