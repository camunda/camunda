/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.simple.UserTaskFilter;
import io.camunda.gateway.protocol.model.simple.VariableValueFilterProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * MCP-specific user task filter that hides fields not exposed to MCP tools.
 *
 * <p>This extends the generated simple filter model and overrides getters with {@code @JsonIgnore}
 * to exclude certain fields from the MCP JSON schema while keeping the underlying mapping code
 * intact.
 */
public class McpUserTaskFilter extends UserTaskFilter {

  private static final String SIMPLE_JSON_EXAMPLE_VALUE = "\\\"myValue\\\"";
  private static final String NESTED_JSON_EXAMPLE_VALUE = "\"{\\\"myVar\\\":\\\"myValue\\\"}\"";

  @Override
  @JsonIgnore
  public String getTenantId() {
    return super.getTenantId();
  }

  @Override
  @JsonIgnore
  public String getCandidateGroup() {
    return super.getCandidateGroup();
  }

  @Override
  @JsonIgnore
  public String getCandidateUser() {
    return super.getCandidateUser();
  }

  @Schema(
      name = "processInstanceVariables",
      description =
          "Filter by process instance variables. Variable values are in serialized JSON format. "
              + "Example string value: "
              + SIMPLE_JSON_EXAMPLE_VALUE
              + ". Example nested JSON value: "
              + NESTED_JSON_EXAMPLE_VALUE,
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Override
  public @Nullable List<VariableValueFilterProperty> getProcessInstanceVariables() {
    return super.getProcessInstanceVariables();
  }

  @Schema(
      name = "localVariables",
      description =
          "Filter by local (user task-scoped) variables. Variable values are in serialized JSON format. "
              + "Example string value: "
              + SIMPLE_JSON_EXAMPLE_VALUE
              + ". Example nested JSON value: "
              + NESTED_JSON_EXAMPLE_VALUE,
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Override
  public @Nullable List<VariableValueFilterProperty> getLocalVariables() {
    return super.getLocalVariables();
  }
}
