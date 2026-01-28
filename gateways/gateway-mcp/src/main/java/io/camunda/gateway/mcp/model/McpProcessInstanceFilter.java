/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields;
import java.util.List;

/**
 * MCP-specific process instance filter extending the {@link ProcessInstanceFilter} to hide fields
 * from MCP clients to avoid unnecessary context bloat.
 */
public class McpProcessInstanceFilter extends ProcessInstanceFilter {

  @JsonIgnore
  @Override
  public String getTenantId() {
    return super.getTenantId();
  }

  @JsonIgnore
  @Override
  public String getParentProcessInstanceKey() {
    return super.getParentProcessInstanceKey();
  }

  @JsonIgnore
  @Override
  public String getParentElementInstanceKey() {
    return super.getParentElementInstanceKey();
  }

  @JsonIgnore
  @Override
  public String getBatchOperationId() {
    return super.getBatchOperationId();
  }

  @JsonIgnore
  @Override
  public String getErrorMessage() {
    return super.getErrorMessage();
  }

  @JsonIgnore
  @Override
  public Boolean getHasRetriesLeft() {
    return super.getHasRetriesLeft();
  }

  @JsonIgnore
  @Override
  public ElementInstanceStateEnum getElementInstanceState() {
    return super.getElementInstanceState();
  }

  @JsonIgnore
  @Override
  public String getElementId() {
    return super.getElementId();
  }

  @JsonIgnore
  @Override
  public Boolean getHasElementInstanceIncident() {
    return super.getHasElementInstanceIncident();
  }

  @JsonIgnore
  @Override
  public Integer getIncidentErrorHashCode() {
    return super.getIncidentErrorHashCode();
  }

  @JsonIgnore
  @Override
  public String getProcessDefinitionVersionTag() {
    return super.getProcessDefinitionVersionTag();
  }

  @JsonIgnore
  @Override
  public List<ProcessInstanceFilterFields> get$Or() {
    return super.get$Or();
  }
}
