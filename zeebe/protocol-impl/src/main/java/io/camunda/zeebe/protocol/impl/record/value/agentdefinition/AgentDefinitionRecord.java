/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentdefinition;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class AgentDefinitionRecord extends UnifiedRecordValue
    implements AgentDefinitionRecordValue {

  private final LongProperty agentDefinitionKeyProp = new LongProperty("agentDefinitionKey", -1L);
  private final EnumProperty<AgentDefinitionType> agentTypeProp =
      new EnumProperty<>("agentType", AgentDefinitionType.class, AgentDefinitionType.UNSPECIFIED);
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final StringProperty processDefinitionVersionTagProp =
      new StringProperty("processDefinitionVersionTag", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public AgentDefinitionRecord() {
    super(9);
    declareProperty(agentDefinitionKeyProp)
        .declareProperty(agentTypeProp)
        .declareProperty(nameProp)
        .declareProperty(elementIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionVersionTagProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKeyProp.getValue();
  }

  public AgentDefinitionRecord setAgentDefinitionKey(final long agentDefinitionKey) {
    agentDefinitionKeyProp.setValue(agentDefinitionKey);
    return this;
  }

  @Override
  public AgentDefinitionType getAgentType() {
    return agentTypeProp.getValue();
  }

  public AgentDefinitionRecord setAgentType(final AgentDefinitionType agentType) {
    agentTypeProp.setValue(agentType);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public AgentDefinitionRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentDefinitionRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public AgentDefinitionRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public AgentDefinitionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  public AgentDefinitionRecord setProcessDefinitionVersion(final int processDefinitionVersion) {
    processDefinitionVersionProp.setValue(processDefinitionVersion);
    return this;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return BufferUtil.bufferAsString(processDefinitionVersionTagProp.getValue());
  }

  public AgentDefinitionRecord setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    processDefinitionVersionTagProp.setValue(processDefinitionVersionTag);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public AgentDefinitionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
