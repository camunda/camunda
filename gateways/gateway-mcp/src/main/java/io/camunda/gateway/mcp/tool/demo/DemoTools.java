/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.demo;

import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParams;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Demo tool to test @McpToolParams annotation.
 *
 * <p>This tool demonstrates how to use @McpToolParams to unwrap a DTO's fields into individual
 * root-level parameters in the MCP tool schema.
 */
@Component
@Validated
public class DemoTools {

  /**
   * Demo tool using @McpToolParams.
   *
   * <p>The MCP schema will expose taskName, priority, metadata, and urgent as individual root-level
   * properties, NOT nested under a "request" property.
   */
  @CamundaMcpTool(
      description =
          """
          Demo tool for creating a task. This tool uses @McpToolParams to unwrap the request DTO.
          All fields (taskName, priority, metadata, urgent) are exposed as flat parameters in the schema.
          """)
  public CallToolResult createTask(@McpToolParams @Valid final CreateTaskRequest request) {
    // Log what we received to verify deserialization worked
    final String message =
        String.format(
            "✅ Task created successfully!\n"
                + "  Name: %s\n"
                + "  Priority: %s\n"
                + "  Urgent: %s\n"
                + "  Metadata: %s",
            request.getTaskName(),
            request.getPriority(),
            request.getUrgent(),
            request.getMetadata() != null ? request.getMetadata() : "none");

    return CallToolResult.builder().addTextContent(message).build();
  }

  /**
   * Comparison tool using traditional @McpToolParam approach.
   *
   * <p>This shows the old way - multiple individual parameters instead of a DTO.
   */
  @CamundaMcpTool(
      description =
          """
          Comparison demo tool using traditional @McpToolParam parameters.
          This is the old way - notice how the method signature has many individual parameters.
          """)
  public CallToolResult createTaskOldWay(
      @McpToolParam(description = "The name of the task", required = true) final String taskName,
      @McpToolParam(description = "Priority of the task", required = true) final String priority,
      @McpToolParam(description = "Optional metadata", required = false)
          final java.util.Map<String, Object> metadata,
      @McpToolParam(description = "Whether urgent", required = true) final Boolean urgent) {

    final String message =
        String.format(
            "✅ Task created (old way)!\n"
                + "  Name: %s\n"
                + "  Priority: %s\n"
                + "  Urgent: %s\n"
                + "  Metadata: %s",
            taskName, priority, urgent, metadata != null ? metadata : "none");

    return CallToolResult.builder().addTextContent(message).build();
  }
}
