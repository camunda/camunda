/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * MCP-specific variable name/value pair for filtering by variable values. Replaces the old protocol
 * model {@code VariableValueFilterProperty} with a flat record that MCP clients can easily
 * construct.
 */
public record McpVariableValue(
    @JsonProperty @JsonPropertyDescription("Name of the variable.") String name,
    @JsonProperty
        @JsonPropertyDescription(
            "The value of the variable. Variable values in filters need to be in serialized JSON format. "
                + "For example, a variable with string value `myValue` can be found with the filter "
                + "value `\"myValue\"`.")
        String value) {}
