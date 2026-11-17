/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agentdefinition;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

/** Secondary-storage entity for AGENT_DEFINITION records. */
public final class AgentDefinitionEntity
    implements ExporterEntity<AgentDefinitionEntity>, TenantOwned {

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long key;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentDefinitionType agentType;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String name;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String elementId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String bpmnProcessId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long processDefinitionKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int processDefinitionVersion;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String processDefinitionVersionTag;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public AgentDefinitionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public AgentDefinitionEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public AgentDefinitionType getAgentType() {
    return agentType;
  }

  public AgentDefinitionEntity setAgentType(final AgentDefinitionType agentType) {
    this.agentType = agentType;
    return this;
  }

  public String getName() {
    return name;
  }

  public AgentDefinitionEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public AgentDefinitionEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public AgentDefinitionEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentDefinitionEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public AgentDefinitionEntity setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public String getProcessDefinitionVersionTag() {
    return processDefinitionVersionTag;
  }

  public AgentDefinitionEntity setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = processDefinitionVersionTag;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public AgentDefinitionEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
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
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (AgentDefinitionEntity) obj;
    return Objects.equals(id, that.id)
        && key == that.key
        && agentType == that.agentType
        && Objects.equals(name, that.name)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && Objects.equals(processDefinitionVersionTag, that.processDefinitionVersionTag)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "AgentDefinitionEntity{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", agentType="
        + agentType
        + ", name='"
        + name
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", processDefinitionVersionTag='"
        + processDefinitionVersionTag
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
