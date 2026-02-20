/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_FILTER_FORMAT_NOTE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.camunda.gateway.protocol.model.simple.VariableFilter;

/**
 * MCP-specific variable filter modifying the {@link VariableFilter} to hide fields from MCP clients
 * to avoid unnecessary context bloat.
 */
@JsonIgnoreProperties("tenantId")
public class McpVariableFilter extends VariableFilter {

  @JsonPropertyDescription(VARIABLE_FILTER_FORMAT_NOTE)
  @Override
  public String getValue() {
    return super.getValue();
  }
}
