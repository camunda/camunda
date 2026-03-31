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
 * MCP-specific process definition filter facade record. Declares only the fields exposed to MCP
 * clients with flat Java types. Converted to strict contract types before passing to the mapping
 * layer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpProcessDefinitionFilter(
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition name.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String name,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter by whether it is the latest version. When set, pagination is forward-only (use `after`/`limit`), and sorting is limited to `processDefinitionId` and `tenantId`.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Boolean isLatestVersion,
    @JsonProperty
        @JsonPropertyDescription("Filter by resource name.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String resourceName,
    @JsonProperty
        @JsonPropertyDescription("Filter by version number.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Integer version,
    @JsonProperty
        @JsonPropertyDescription("Filter by version tag.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String versionTag,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition ID (BPMN process ID).")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionId,
    @JsonProperty
        @JsonPropertyDescription("Filter by process definition key.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        String processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription("Filter by whether the process definition has a start form.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Boolean hasStartForm) {}
