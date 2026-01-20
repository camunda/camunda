/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.simple.IncidentFilter;

/**
 * MCP-specific Incident filter extending the {@link IncidentFilter} to hide fields from MCP clients
 * to avoid unnecessary context bloat.
 */
public class McpIncidentFilter extends IncidentFilter {

  @JsonIgnore
  @Override
  public String getErrorMessage() {
    return super.getErrorMessage();
  }

  @JsonIgnore
  @Override
  public String getTenantId() {
    return super.getTenantId();
  }

  @JsonIgnore
  @Override
  public String getIncidentKey() {
    return super.getIncidentKey();
  }

  @JsonIgnore
  @Override
  public String getElementInstanceKey() {
    return super.getElementInstanceKey();
  }

  @JsonIgnore
  @Override
  public String getJobKey() {
    return super.getJobKey();
  }
}
