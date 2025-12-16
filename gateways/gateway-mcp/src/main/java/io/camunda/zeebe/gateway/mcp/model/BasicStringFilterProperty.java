/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(
    name = "BasicStringFilterProperty",
    description = "String property with basic advanced search capabilities.")
public record BasicStringFilterProperty(
    @Nullable
        @Schema(
            name = "eq",
            description = "Checks for equality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String eq,
    @Nullable
        @Schema(
            name = "neq",
            description = "Checks for inequality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String neq,
    @Nullable
        @Schema(
            name = "exists",
            description = "Checks if the current property exists.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean exists,
    @Nullable
        @Valid
        @Schema(
            name = "in",
            description = "Checks if the property matches any of the provided values.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank String> in,
    @Nullable
        @Valid
        @Schema(
            name = "notIn",
            description = "Checks if the property matches none of the provided values.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank String> notIn) {}
