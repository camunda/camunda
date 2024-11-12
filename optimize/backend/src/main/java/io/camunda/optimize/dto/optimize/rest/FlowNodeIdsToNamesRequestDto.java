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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
