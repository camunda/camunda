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
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;

/**
 * Marks a method as an MCP tool.
 *
 * <p>This is our custom tool annotation that is functionally equivalent to Spring AI's {@link
 * McpTool} but allows to control schema generation and output handling individually.
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
   * MCP protocol annotations providing hints about the tool's behavior.
   *
   * @return the tool annotations
   */
  McpAnnotations annotations() default @McpAnnotations;

  /**
   * Whether to generate output schema for the tool's return type.
   *
   * @return true if output schema should be generated
   */
  boolean generateOutputSchema() default false;
}
