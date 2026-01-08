/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class HelloWorldTools {

  private static final Logger LOG = LoggerFactory.getLogger(HelloWorldTools.class);

  @McpTool(
      description = "Hello world tool",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public String helloWorld(@McpToolParam(description = "Your name") @NotBlank final String name) {
    LOG.debug("MCP: Running helloWorld tool for name: {}", name);
    return "Hello, " + name;
  }
}
