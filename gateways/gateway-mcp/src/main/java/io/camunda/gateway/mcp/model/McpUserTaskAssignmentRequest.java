/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;

/**
 * MCP-specific user task assignment request that hides the assignee field from the MCP JSON schema.
 *
 * <p>The assignee is exposed as a separate root-level tool parameter with its own description. This
 * class contains only the options (allowOverride and action), allowing us to reuse the schema
 * descriptions from the API spec without duplication.
 */
public class McpUserTaskAssignmentRequest extends UserTaskAssignmentRequest {

  @Override
  @JsonIgnore
  public String getAssignee() {
    return super.getAssignee();
  }
}
