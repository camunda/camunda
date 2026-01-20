/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.simple.VariableFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * MCP-specific variable filter extending the {@link VariableFilter} to hide fields from MCP clients
 * to avoid unnecessary context bloat.
 */
public class McpVariableFilter extends VariableFilter {

  private static final String SIMPLE_JSON_EXAMPLE_VALUE = "\\\"myValue\\\"";
  private static final String NESTED_JSON_EXAMPLE_VALUE = "\"{\\\"myVar\\\":\\\"myValue\\\"}\"";

  @JsonIgnore
  @Override
  public String getTenantId() {
    return super.getTenantId();
  }

  @Schema(
      name = "value",
      description =
          "The value of the variable in serialized JSON format. Example string value: "
              + SIMPLE_JSON_EXAMPLE_VALUE
              + ". Example nested JSON value: "
              + NESTED_JSON_EXAMPLE_VALUE,
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Override
  public @Nullable String getValue() {
    return super.getValue();
  }
}
