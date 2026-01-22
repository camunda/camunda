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

/**
 * MCP-specific user task filter that hides fields not exposed to MCP tools.
 *
 * <p>This extends the generated simple filter model and overrides getters with {@code @JsonIgnore}
 * to exclude certain fields from the MCP JSON schema while keeping the underlying mapping code
 * intact.
 */
public class McpUserTaskFilter extends UserTaskFilter {

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
}
