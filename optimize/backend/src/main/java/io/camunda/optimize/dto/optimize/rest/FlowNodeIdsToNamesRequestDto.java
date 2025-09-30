/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.List;
import java.util.Objects;

public class FlowNodeIdsToNamesRequestDto {

  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected String tenantId;
  protected List<String> nodeIds;

  public FlowNodeIdsToNamesRequestDto() {}

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public List<String> getNodeIds() {
    return nodeIds;
  }

  public void setNodeIds(final List<String> nodeIds) {
    this.nodeIds = nodeIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeIdsToNamesRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeIdsToNamesRequestDto that = (FlowNodeIdsToNamesRequestDto) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionVersion, that.processDefinitionVersion)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(nodeIds, that.nodeIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionKey, processDefinitionVersion, tenantId, nodeIds);
  }

  @Override
  public String toString() {
    return "FlowNodeIdsToNamesRequestDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersion="
        + getProcessDefinitionVersion()
        + ", tenantId="
        + getTenantId()
        + ", nodeIds="
        + getNodeIds()
        + ")";
  }
}
