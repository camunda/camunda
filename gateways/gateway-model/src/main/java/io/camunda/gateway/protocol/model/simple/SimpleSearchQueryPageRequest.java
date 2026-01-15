/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model.simple;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

@Schema(
    name = "SimpleSearchQueryPageRequest",
    description =
        "Pagination configuration for search queries. Supports cursor-based pagination (before/after), offset-based pagination (from), or simple limit-based pagination.")
public record SimpleSearchQueryPageRequest(
    @Nullable
        @Pattern(regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$")
        @Size(min = 2, max = 300)
        @Schema(
            name = "before",
            example = "WzIyNTE3OTk4MTM2ODcxMDJd",
            description =
                "Use the `startCursor` value from the previous response to fetch the previous page of results.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String before,
    @Nullable
        @Pattern(regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$")
        @Size(min = 2, max = 300)
        @Schema(
            name = "after",
            example = "WzIyNTE3OTk4MTM2ODcxMDJd",
            description =
                "Use the `endCursor` value from the previous response to fetch the next page of results.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String after,
    @Nullable
        @Min(value = 0)
        @Schema(
            name = "from",
            description = "The index of items to start searching from (offset-based pagination).",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer from,
    @Nullable
        @Min(value = 1)
        @Max(value = 100)
        @Schema(
            name = "limit",
            description = "The maximum number of items to return in one request.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer limit) {}
