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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecordV1;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class AgentInstanceRecordV1 extends UnifiedRecordValue
    implements AgentInstanceRecordValue {

  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final LongProperty agentDefinitionKeyProp = new LongProperty("agentDefinitionKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final ArrayProperty<LongValue> elementInstanceKeysProp =
      new ArrayProperty<>("elementInstanceKeys", LongValue::new);
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty rootProcessInstanceKeyProp =
      new LongProperty("rootProcessInstanceKey", -1L);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final StringProperty processDefinitionVersionTagProp =
      new StringProperty("processDefinitionVersionTag", "");
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
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final StringProperty jobLeaseTokenProp = new StringProperty("jobLeaseToken", "");
  private final ArrayProperty<AgentHistoryRecordV1> historyProp =
      new ArrayProperty<>("history", AgentHistoryRecordV1::new);

  public AgentInstanceRecordV1() {
    super(22);
    declareProperty(agentInstanceKeyProp)
        .declareProperty(agentDefinitionKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(elementInstanceKeysProp)
        .declareProperty(elementIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(rootProcessInstanceKeyProp)
        .declareProperty(storageOrdinalProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionVersionTagProp)
        .declareProperty(tenantIdProp)
        .declareProperty(statusProp)
        .declareProperty(definitionProp)
        .declareProperty(limitsProp)
        .declareProperty(metricsProp)
        .declareProperty(toolsProp)
        .declareProperty(changedAttributesProp)
        .declareProperty(jobKeyProp)
        .declareProperty(jobLeaseTokenProp)
        .declareProperty(historyProp);
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setAgentInstanceKey(final long agentInstanceKey) {
    agentInstanceKeyProp.setValue(agentInstanceKey);
    return this;
  }

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setAgentDefinitionKey(final long agentDefinitionKey) {
    agentDefinitionKeyProp.setValue(agentDefinitionKey);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeysProp.stream().map(LongValue::getValue).toList();
  }

  public AgentInstanceRecordV1 setElementInstanceKeys(final List<Long> elementInstanceKeys) {
    elementInstanceKeysProp.reset();
    if (elementInstanceKeys != null) {
      elementInstanceKeys.forEach(k -> elementInstanceKeysProp.add().setValue(k));
    }
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentInstanceRecordV1 setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public AgentInstanceRecordV1 setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  public AgentInstanceRecordV1 setProcessDefinitionVersion(final int processDefinitionVersion) {
    processDefinitionVersionProp.setValue(processDefinitionVersion);
    return this;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return BufferUtil.bufferAsString(processDefinitionVersionTagProp.getValue());
  }

  public AgentInstanceRecordV1 setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    processDefinitionVersionTagProp.setValue(processDefinitionVersionTag);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public AgentInstanceRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return statusProp.getValue();
  }

  public AgentInstanceRecordV1 setStatus(final AgentInstanceStatus status) {
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

  public AgentInstanceRecordV1 setTools(final List<? extends AgentInstanceToolValue> tools) {
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

  public AgentInstanceRecordV1 setChangedAttributes(final List<String> changedAttributes) {
    changedAttributesProp.reset();
    if (changedAttributes != null) {
      changedAttributes.forEach(
          attr -> changedAttributesProp.add().wrap(BufferUtil.wrapString(attr)));
    }
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public AgentInstanceRecordV1 setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
    return this;
  }

  @Override
  public String getJobLeaseToken() {
    return BufferUtil.bufferAsString(jobLeaseTokenProp.getValue());
  }

  public AgentInstanceRecordV1 setJobLeaseToken(final String jobLeaseToken) {
    jobLeaseTokenProp.setValue(jobLeaseToken);
    return this;
  }

  @Override
  public List<AgentHistoryRecordValue> getHistory() {
    return historyProp.stream().collect(Collectors.toList());
  }

  public AgentInstanceRecordV1 setHistory(final List<? extends AgentHistoryRecordV1> history) {
    historyProp.reset();
    if (history != null) {
      for (final var item : history) {
        historyProp.add().copyFrom(item);
      }
    }
    return this;
  }

  public AgentInstanceRecordV1 setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    rootProcessInstanceKeyProp.setValue(rootProcessInstanceKey);
    return this;
  }

  public AgentInstanceRecordV1 addElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeysProp.add().setValue(elementInstanceKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public AgentInstanceRecordV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }

  public AgentInstanceRecordV1 addChangedAttribute(final String attribute) {
    changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute));
    return this;
  }

  public AgentInstanceRecordV1 addHistoryItem(final AgentHistoryRecordV1 historyItem) {
    historyProp.add().copyFrom(historyItem);
    return this;
  }
}
