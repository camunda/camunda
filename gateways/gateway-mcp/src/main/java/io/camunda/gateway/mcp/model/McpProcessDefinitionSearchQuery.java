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
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.SearchQueryPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * MCP-specific process definition search query facade. Wraps the MCP-owned filter with existing
 * page/sort types.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpProcessDefinitionSearchQuery(
    @JsonProperty
        @JsonPropertyDescription("The process definition search filters.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpProcessDefinitionFilter filter,
    @JsonProperty
        @JsonPropertyDescription("Pagination criteria.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        SearchQueryPageRequest page,
    @JsonProperty
        @JsonPropertyDescription("Sort field criteria.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        List<ProcessDefinitionSearchQuerySortRequest> sort) {}
