/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AgentInstanceRecord extends UnifiedRecordValue
    implements AgentInstanceRecordValue {

  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final ArrayProperty<LongValue> elementInstanceKeysProp =
      new ArrayProperty<>("elementInstanceKeys", LongValue::new);
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final EnumProperty<AgentInstanceStatus> statusProp =
      new EnumProperty<>("status", AgentInstanceStatus.class, AgentInstanceStatus.UNSPECIFIED);
  private final ObjectProperty<AgentInstanceDefinition> definitionProp =
      new ObjectProperty<>("definition", new AgentInstanceDefinition());
  private final ObjectProperty<AgentInstanceLimits> limitsProp =
      new ObjectProperty<>("limits", new AgentInstanceLimits());
  private final ObjectProperty<AgentInstanceMetrics> metricsProp =
      new ObjectProperty<>("metrics", new AgentInstanceMetrics());
  private final ArrayProperty<AgentInstanceTool> toolsProp =
      new ArrayProperty<>("tools", AgentInstanceTool::new);
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>("changedAttributes", StringValue::new);

  public AgentInstanceRecord() {
    super(16);
    declareProperty(agentInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(elementInstanceKeysProp)
        .declareProperty(elementIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(versionTagProp)
        .declareProperty(tenantIdProp)
        .declareProperty(statusProp)
        .declareProperty(definitionProp)
        .declareProperty(limitsProp)
        .declareProperty(metricsProp)
        .declareProperty(toolsProp)
        .declareProperty(changedAttributesProp);
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setAgentInstanceKey(final long agentInstanceKey) {
    agentInstanceKeyProp.setValue(agentInstanceKey);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeysProp.stream().map(LongValue::getValue).toList();
  }

  public AgentInstanceRecord setElementInstanceKeys(final List<Long> elementInstanceKeys) {
    elementInstanceKeysProp.reset();
    if (elementInstanceKeys != null) {
      elementInstanceKeys.forEach(k -> elementInstanceKeysProp.add().setValue(k));
    }
    return this;
  }

  public AgentInstanceRecord addElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeysProp.add().setValue(elementInstanceKey);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentInstanceRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public AgentInstanceRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public AgentInstanceRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  public AgentInstanceRecord setProcessDefinitionVersion(final int processDefinitionVersion) {
    processDefinitionVersionProp.setValue(processDefinitionVersion);
    return this;
  }

  @Override
  public String getVersionTag() {
    return BufferUtil.bufferAsString(versionTagProp.getValue());
  }

  public AgentInstanceRecord setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public AgentInstanceRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return statusProp.getValue();
  }

  public AgentInstanceRecord setStatus(final AgentInstanceStatus status) {
    statusProp.setValue(status);
    return this;
  }

  @Override
  public AgentInstanceDefinition getDefinition() {
    return definitionProp.getValue();
  }

  @Override
  public AgentInstanceLimits getLimits() {
    return limitsProp.getValue();
  }

  @Override
  public AgentInstanceMetrics getMetrics() {
    return metricsProp.getValue();
  }

  @Override
  public List<AgentInstanceToolValue> getTools() {
    return toolsProp.stream()
        .map(
            element -> {
              final var copy = new AgentInstanceTool();
              copy.copy(element);
              return (AgentInstanceToolValue) copy;
            })
        .toList();
  }

  public AgentInstanceRecord setTools(final List<? extends AgentInstanceToolValue> tools) {
    toolsProp.reset();
    for (final var tool : tools) {
      toolsProp.add().copy(tool);
    }
    return this;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributesProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public AgentInstanceRecord setChangedAttributes(final List<String> changedAttributes) {
    changedAttributesProp.reset();
    if (changedAttributes != null) {
      changedAttributes.forEach(
          attr -> changedAttributesProp.add().wrap(BufferUtil.wrapString(attr)));
    }
    return this;
  }

  public AgentInstanceRecord addChangedAttribute(final String attribute) {
    changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute));
    return this;
  }
}
