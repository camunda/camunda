/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.agentdefinition;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ZeebeAgentDefinitionDataDto implements AgentDefinitionRecordValue {

  private long agentDefinitionKey;
  private AgentDefinitionType agentType;
  private String name;
  private String elementId;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private int processDefinitionVersion;
  private String processDefinitionVersionTag;
  private String tenantId;

  public ZeebeAgentDefinitionDataDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKey;
  }

  public void setAgentDefinitionKey(final long agentDefinitionKey) {
    this.agentDefinitionKey = agentDefinitionKey;
  }

  @Override
  public AgentDefinitionType getAgentType() {
    return agentType;
  }

  public void setAgentType(final AgentDefinitionType agentType) {
    this.agentType = agentType;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return processDefinitionVersionTag;
  }

  public void setProcessDefinitionVersionTag(final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = processDefinitionVersionTag;
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentDefinitionKey,
        agentType,
        name,
        elementId,
        bpmnProcessId,
        processDefinitionKey,
        processDefinitionVersion,
        processDefinitionVersionTag,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeAgentDefinitionDataDto that = (ZeebeAgentDefinitionDataDto) o;
    return agentDefinitionKey == that.agentDefinitionKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && agentType == that.agentType
        && Objects.equals(name, that.name)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionVersionTag, that.processDefinitionVersionTag)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ZeebeAgentDefinitionDataDto(agentDefinitionKey="
        + agentDefinitionKey
        + ", agentType="
        + agentType
        + ", name="
        + name
        + ", elementId="
        + elementId
        + ", bpmnProcessId="
        + bpmnProcessId
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", tenantId="
        + tenantId
        + ")";
  }
}
