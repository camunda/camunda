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
 * MCP-specific user task assignment options. Contains only the optional assignment configuration
 * fields; the assignee is provided as a separate root-level tool parameter.
 */
public record McpUserTaskAssignmentOptions(
    @JsonProperty
        @JsonPropertyDescription(
            "By default, the task is reassigned if it was already assigned. Set this to false to "
                + "return an error in such cases. The task must then first be unassigned to be "
                + "assigned again. Use this when you have users picking from group task queues to "
                + "prevent race conditions.")
        Boolean allowOverride,
    @JsonProperty
        @JsonPropertyDescription(
            "A custom action value that will be accessible from user task events resulting from "
                + "this endpoint invocation. If not provided, it will default to \"assign\".")
        String action) {}
