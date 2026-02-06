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
import org.springaicommunity.mcp.annotation.McpTool;

/**
 * Marks a method as an MCP tool.
 *
 * <p>This is Camunda's custom tool annotation that is functionally equivalent to Spring AI's
 * {@code @McpTool} but allows Camunda to control tool registration independently without relying on
 * Spring AI's autoconfig exclusions.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @CamundaMcpTool(
 *     description = "Search for process instances.",
 *     annotations = @McpTool.McpAnnotations(readOnlyHint = true))
 * public CallToolResult searchProcessInstances(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CamundaMcpTool {

  /**
   * Optional tool name. If not specified, the method name will be used.
   *
   * @return the tool name
   */
  String name() default "";

  /**
   * Description of what the tool does. This is shown to AI models to help them understand when to
   * use the tool.
   *
   * @return the tool description
   */
  String description() default "";

  /**
   * Optional tool title for display purposes.
   *
   * @return the tool title
   */
  String title() default "";

  /**
   * MCP protocol annotations providing hints about the tool's behavior. Reuses Spring AI's {@link
   * McpTool.McpAnnotations} to avoid duplication.
   *
   * @return the tool annotations
   */
  McpTool.McpAnnotations annotations() default @McpTool.McpAnnotations;

  /**
   * Whether to generate output schema for the tool's return type.
   *
   * @return true if output schema should be generated
   */
  boolean generateOutputSchema() default false;
}
