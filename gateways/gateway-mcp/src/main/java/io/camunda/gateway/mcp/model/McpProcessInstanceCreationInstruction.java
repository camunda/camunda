/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import io.camunda.gateway.mcp.tool.ToolDescriptions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springaicommunity.mcp.annotation.McpToolParam;

public record McpProcessInstanceCreationInstruction(
    @McpToolParam(
            description = ToolDescriptions.PROCESS_DEFINITION_KEY_DESCRIPTION,
            required = false)
        String processDefinitionKey,
    @McpToolParam(
            description = "The BPMN process id of the process definition to start an instance of.",
            required = false)
        @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_\\-.]*$")
        @Size(min = 1)
        String processDefinitionId,
    @McpToolParam(
            description =
                "The version of the process. By default, the latest version of the process is used. Can only be used in combination with processDefinitionId.",
            required = false)
        Integer processDefinitionVersion,
    @McpToolParam(
            description =
                "Set of variables to instantiate in the root variable scope of the process instance. Can include nested/complex objects. Which variables to set depends on the process definition.",
            required = false)
        Map<@NotBlank String, Object> variables,
    @McpToolParam(
            description =
                "Wait for the process instance to complete. If the process instance does not complete within request timeout limits, the waiting will time out and the tool will return a 504 response status. Use the unique tag you added to query process instance status. Disabled by default.",
            required = false)
        Boolean awaitCompletion,
    @McpToolParam(
            description =
                "List of variables by name to be included in the response when awaitCompletion is set to true. If empty, all visible variables in the root scope will be returned.",
            required = false)
        List<@NotBlank String> fetchVariables,
    @McpToolParam(
            description =
                "List of tags to apply to the process instance. Tags must start with a letter, followed by letters, digits, or the special characters `_`, `-`, `:`, or `.`; length ≤ 100.",
            required = false)
        Set<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_\\-:.]{0,99}$") @Size(min = 1, max = 100) String>
            tags) {}
