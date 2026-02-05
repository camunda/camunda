/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.demo;

import io.camunda.gateway.mcp.annotation.McpRequestBody;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Demo tool to test @McpRequestBody annotation.
 *
 * <p>This tool demonstrates how to use @McpRequestBody to unwrap a DTO's fields into individual
 * root-level parameters in the MCP tool schema.
 */
@Component
@Validated
public class DemoTools {

  /**
   * Demo tool using @McpRequestBody.
   *
   * <p>The MCP schema will expose taskName, priority, metadata, and urgent as individual root-level
   * properties, NOT nested under a "request" property.
   */
  @McpTool(
      description =
          """
          Demo tool for creating a task. This tool uses @McpRequestBody to unwrap the request DTO.
          All fields (taskName, priority, metadata, urgent) are exposed as flat parameters in the schema.
          """)
  public CallToolResult createTask(@McpRequestBody CreateTaskRequest request) {
    // Log what we received to verify deserialization worked
    String message =
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
  @McpTool(
      description =
          """
          Comparison demo tool using traditional @McpToolParam parameters.
          This is the old way - notice how the method signature has many individual parameters.
          """)
  public CallToolResult createTaskOldWay(
      @org.springaicommunity.mcp.annotation.McpToolParam(
              description = "The name of the task",
              required = true)
          String taskName,
      @org.springaicommunity.mcp.annotation.McpToolParam(
              description = "Priority of the task",
              required = true)
          String priority,
      @org.springaicommunity.mcp.annotation.McpToolParam(
              description = "Optional metadata",
              required = false)
          java.util.Map<String, Object> metadata,
      @org.springaicommunity.mcp.annotation.McpToolParam(
              description = "Whether urgent",
              required = true)
          Boolean urgent) {

    String message =
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
