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
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP-specific process instance creation instruction. Contains only the fields exposed to MCP
 * clients, excluding internal-only fields like {@code operationReference}, {@code
 * startInstructions}, and {@code runtimeInstructions}.
 */
public record McpProcessInstanceCreation(
    @JsonProperty
        @JsonPropertyDescription(
            "The unique key identifying the process definition, for example, returned for a "
                + "process in the deploy resources endpoint.")
        String processDefinitionKey,
    @JsonProperty
        @JsonPropertyDescription(
            "The BPMN process id of the process definition to start an instance of.")
        String processDefinitionId,
    @JsonProperty
        @JsonPropertyDescription(
            "The version of the process. By default, the latest version of the process is used.")
        Integer processDefinitionVersion,
    @JsonProperty
        @JsonPropertyDescription(
            "JSON object that will instantiate the variables for the root variable scope of the "
                + "process instance.")
        Map<String, Object> variables,
    @JsonProperty
        @JsonPropertyDescription(
            "The tenant id of the process definition. If multi-tenancy is enabled, provide the "
                + "tenant id of the process definition to start a process instance of.")
        @Pattern(regexp = "^(<default>|[A-Za-z0-9_@.+-]+)$")
        String tenantId,
    @JsonProperty
        @JsonPropertyDescription(
            "Wait for the process instance to complete. If the process instance does not complete "
                + "within the request timeout limit, a 504 response status will be returned. "
                + "Disabled by default.")
        Boolean awaitCompletion,
    @JsonProperty
        @JsonPropertyDescription(
            "List of variables by name to be included in the response when awaitCompletion is set "
                + "to true. If empty, all visible variables in the root scope will be returned.")
        List<String> fetchVariables,
    @JsonProperty
        @JsonPropertyDescription(
            "Timeout (in ms) the request waits for the process to complete. By default or when set "
                + "to 0, the generic request timeout configured in the cluster is applied.")
        Long requestTimeout,
    @JsonProperty
        @JsonPropertyDescription(
            "List of tags. Tags need to start with a letter; then alphanumerics, `_`, `-`, `:`, "
                + "or `.`; length <= 100.")
        Set<String> tags,
    @JsonProperty
        @JsonPropertyDescription(
            "An optional, user-defined string identifier that identifies the process instance "
                + "within the scope of a process definition.")
        String businessId) {}
