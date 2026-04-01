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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSearchQueryPageRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchQuerySortRequestStrictContract;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * MCP-specific variable search query facade. Wraps the MCP-owned filter with existing page/sort
 * types. Used with {@code @McpToolParamsUnwrapped} to present filter/page/sort as top-level tool
 * parameters.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record McpVariableSearchQuery(
    @JsonProperty
        @JsonPropertyDescription("The variable search filters.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        McpVariableFilter filter,
    @JsonProperty
        @JsonPropertyDescription("Pagination criteria.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        GeneratedSearchQueryPageRequestStrictContract page,
    @JsonProperty
        @JsonPropertyDescription("Sort field criteria.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        List<GeneratedVariableSearchQuerySortRequestStrictContract> sort,
    @JsonProperty
        @JsonPropertyDescription(
            "If true (default), variable values longer than 8191 characters will be truncated. Set to false to return full values.")
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "##default")
        Boolean truncateValues) {}
