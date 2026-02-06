/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for unwrapping DTO parameters in MCP tools.
 *
 * <p>When applied to a parameter, instead of treating the parameter as a single nested object in
 * the tool's JSON schema, its properties are unwrapped and included directly at the root level of
 * the input schema.
 *
 * <p>This is useful for tools that want to use a DTO for type safety and validation but present a
 * flatter, more user-friendly schema to MCP clients.
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>{@code
 * @CamundaMcpTool(description = "Create a task")
 * public CallToolResult createTask(@McpToolParams @Valid CreateTaskRequest request) {
 *   // request.taskName, request.description are unwrapped to root level in schema
 *   return taskService.create(request);
 * }
 * }</pre>
 *
 * <h3>Schema Generation:</h3>
 *
 * The MCP tool schema will expose the DTO's fields as individual root-level properties:
 *
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "taskName": { "type": "string" },
 *     "description": { "type": "string" }
 *   }
 * }
 * }</pre>
 *
 * <h3>Limitations:</h3>
 *
 * <ul>
 *   <li>Only one parameter per method can be annotated with {@code @McpToolParams}
 *   <li>Cannot be combined with other tool-input parameters (neither simple nor complex); the only
 *       parameters allowed alongside {@code @McpToolParams} are MCP framework types such as {@code
 *       McpSyncRequestContext}, {@code CallToolRequest}, {@code McpMeta}, etc.
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolParams {}
