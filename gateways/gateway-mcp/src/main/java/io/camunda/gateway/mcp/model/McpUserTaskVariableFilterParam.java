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
 * MCP-specific user task variable filter for the {@code searchUserTaskVariables} tool. Provides a
 * simple name-based filter without nested filter property wrappers.
 */
public record McpUserTaskVariableFilterParam(
    @JsonProperty @JsonPropertyDescription("Name of the variable.") String name) {}
