/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.List;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersion = getProcessDefinitionVersion();
    result =
        result * PRIME
            + ($processDefinitionVersion == null ? 43 : $processDefinitionVersion.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $nodeIds = getNodeIds();
    result = result * PRIME + ($nodeIds == null ? 43 : $nodeIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeIdsToNamesRequestDto)) {
      return false;
    }
    final FlowNodeIdsToNamesRequestDto other = (FlowNodeIdsToNamesRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionVersion = getProcessDefinitionVersion();
    final Object other$processDefinitionVersion = other.getProcessDefinitionVersion();
    if (this$processDefinitionVersion == null
        ? other$processDefinitionVersion != null
        : !this$processDefinitionVersion.equals(other$processDefinitionVersion)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$nodeIds = getNodeIds();
    final Object other$nodeIds = other.getNodeIds();
    if (this$nodeIds == null ? other$nodeIds != null : !this$nodeIds.equals(other$nodeIds)) {
      return false;
    }
    return true;
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
