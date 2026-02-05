/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that a method parameter should be bound to the entire MCP tool request
 * body, with its fields exposed as individual parameters in the tool's JSON schema.
 *
 * <p>This annotation is used on MCP tool method parameters that are DTOs, allowing for cleaner
 * method signatures while maintaining flat parameter structures in the MCP tool schema.
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>{@code
 * @McpTool(description = "Create a process instance")
 * public CallToolResult createProcessInstance(
 *     @McpRequestBody ProcessInstanceCreationInstruction instruction) {
 *   // instruction is populated from all input parameters
 *   return processService.create(instruction);
 * }
 * }</pre>
 *
 * <h3>Schema Generation:</h3>
 *
 * The MCP tool schema will expose the DTO's fields as individual root-level properties, NOT as a
 * nested object:
 *
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "processDefinitionKey": { "type": "string" },
 *     "variables": { "type": "object" },
 *     "awaitCompletion": { "type": "boolean" }
 *   }
 * }
 * }</pre>
 *
 * <h3>Method Invocation:</h3>
 *
 * When the tool is invoked, all input parameters are deserialized into a single instance of the
 * annotated DTO type.
 *
 * <h3>Validation:</h3>
 *
 * Bean validation annotations on the DTO's fields (e.g., {@code @NotNull}, {@code @Pattern}) are
 * respected and will be enforced during invocation.
 *
 * <h3>Limitations:</h3>
 *
 * <ul>
 *   <li>Only one parameter per method can be annotated with {@code @McpRequestBody}
 *   <li>Cannot be combined with other {@code @McpToolParam} parameters (all parameters must come
 *       from the DTO)
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpRequestBody {

  /**
   * Whether the request body is required.
   *
   * <p>Default is {@code true}, meaning the tool requires at least some input parameters.
   *
   * @return true if the request body is required, false otherwise
   */
  boolean required() default true;
}
