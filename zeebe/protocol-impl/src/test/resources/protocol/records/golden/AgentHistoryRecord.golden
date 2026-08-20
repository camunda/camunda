/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceLimits;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AgentHistoryRecord extends UnifiedRecordValue
    implements AgentHistoryRecordValue {

  private final LongProperty agentHistoryKeyProp = new LongProperty("agentHistoryKey", -1L);
  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty rootProcessInstanceKeyProp =
      new LongProperty("rootProcessInstanceKey", -1L);
  private final IntegerProperty storageOrdinalKeyProp = new IntegerProperty("storageOrdinalKey", 0);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final StringProperty jobLeaseProp = new StringProperty("jobLease", "");
  private final IntegerProperty loopIterationProp = new IntegerProperty("loopIteration", 0);
  private final EnumProperty<AgentHistoryRole> roleProp =
      new EnumProperty<>("role", AgentHistoryRole.class, AgentHistoryRole.UNSPECIFIED);
  private final LongProperty producedAtProp = new LongProperty("producedAt", -1L);
  private final ArrayProperty<AgentHistoryMessageContent> contentProp =
      new ArrayProperty<>("content", AgentHistoryMessageContent::new);
  private final ArrayProperty<AgentHistoryMessageContent> systemPromptProp =
      new ArrayProperty<>("systemPrompt", AgentHistoryMessageContent::new);
  private final ArrayProperty<AgentHistoryEmbeddedToolCall> toolCallsProp =
      new ArrayProperty<>("toolCalls", AgentHistoryEmbeddedToolCall::new);
  private final ObjectProperty<AgentHistoryMetrics> metricsProp =
      new ObjectProperty<>("metrics", new AgentHistoryMetrics());
  private final StringProperty historyItemIdProp = new StringProperty("historyItemId", "");
  private final ArrayProperty<AgentInstanceTool> toolsProp =
      new ArrayProperty<>("tools", AgentInstanceTool::new);
  private final StringProperty modelProp = new StringProperty("model", "");
  private final StringProperty providerProp = new StringProperty("provider", "");
  private final ObjectProperty<AgentInstanceLimits> limitsProp =
      new ObjectProperty<>("limits", new AgentInstanceLimits());
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>("changedAttributes", StringValue::new);
  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);

  public AgentHistoryRecord() {
    super(25);
    declareProperty(agentHistoryKeyProp)
        .declareProperty(agentInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(rootProcessInstanceKeyProp)
        .declareProperty(storageOrdinalKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(jobKeyProp)
        .declareProperty(jobLeaseProp)
        .declareProperty(loopIterationProp)
        .declareProperty(roleProp)
        .declareProperty(producedAtProp)
        .declareProperty(contentProp)
        .declareProperty(systemPromptProp)
        .declareProperty(toolCallsProp)
        .declareProperty(metricsProp)
        .declareProperty(historyItemIdProp)
        .declareProperty(toolsProp)
        .declareProperty(modelProp)
        .declareProperty(providerProp)
        .declareProperty(limitsProp)
        .declareProperty(changedAttributesProp)
        .declareProperty(isDuplicateProp);
  }

  @Override
  public long getAgentHistoryKey() {
    return agentHistoryKeyProp.getValue();
  }

  public AgentHistoryRecord setAgentHistoryKey(final long agentHistoryKey) {
    agentHistoryKeyProp.setValue(agentHistoryKey);
    return this;
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentHistoryRecord setAgentInstanceKey(final long agentInstanceKey) {
    agentInstanceKeyProp.setValue(agentInstanceKey);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public AgentHistoryRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public AgentHistoryRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProp.getValue();
  }

  public AgentHistoryRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    rootProcessInstanceKeyProp.setValue(rootProcessInstanceKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public AgentHistoryRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public AgentHistoryRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public AgentHistoryRecord setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
    return this;
  }

  @Override
  public String getJobLease() {
    return BufferUtil.bufferAsString(jobLeaseProp.getValue());
  }

  public AgentHistoryRecord setJobLease(final String jobLease) {
    jobLeaseProp.setValue(jobLease);
    return this;
  }

  @Override
  public int getLoopIteration() {
    return loopIterationProp.getValue();
  }

  public AgentHistoryRecord setLoopIteration(final int loopIteration) {
    loopIterationProp.setValue(loopIteration);
    return this;
  }

  @Override
  public AgentHistoryRole getRole() {
    return roleProp.getValue();
  }

  public AgentHistoryRecord setRole(final AgentHistoryRole role) {
    roleProp.setValue(role);
    return this;
  }

  @Override
  public long getProducedAt() {
    return producedAtProp.getValue();
  }

  public AgentHistoryRecord setProducedAt(final long producedAt) {
    producedAtProp.setValue(producedAt);
    return this;
  }

  @Override
  public List<AgentHistoryMessageContentValue> getContent() {
    return contentProp.stream()
        .map(
            element -> {
              final var copy = new AgentHistoryMessageContent();
              copy.copy(element);
              return (AgentHistoryMessageContentValue) copy;
            })
        .toList();
  }

  public AgentHistoryRecord setContent(
      final List<? extends AgentHistoryMessageContentValue> content) {
    contentProp.reset();
    for (final var item : content) {
      contentProp.add().copy(item);
    }
    return this;
  }

  @Override
  public List<AgentHistoryMessageContentValue> getSystemPrompt() {
    return systemPromptProp.stream()
        .map(
            element -> {
              final var copy = new AgentHistoryMessageContent();
              copy.copy(element);
              return (AgentHistoryMessageContentValue) copy;
            })
        .toList();
  }

  public AgentHistoryRecord setSystemPrompt(
      final List<? extends AgentHistoryMessageContentValue> systemPrompt) {
    systemPromptProp.reset();
    if (systemPrompt != null) {
      for (final var item : systemPrompt) {
        systemPromptProp.add().copy(item);
      }
    }
    return this;
  }

  @Override
  public List<AgentHistoryEmbeddedToolCallValue> getToolCalls() {
    return toolCallsProp.stream()
        .map(
            element -> {
              final var copy = new AgentHistoryEmbeddedToolCall();
              copy.copy(element);
              return (AgentHistoryEmbeddedToolCallValue) copy;
            })
        .toList();
  }

  public AgentHistoryRecord setToolCalls(
      final List<? extends AgentHistoryEmbeddedToolCallValue> toolCalls) {
    toolCallsProp.reset();
    for (final var item : toolCalls) {
      toolCallsProp.add().copy(item);
    }
    return this;
  }

  @Override
  public AgentHistoryMetrics getMetrics() {
    return metricsProp.getValue();
  }

  @Override
  public String getHistoryItemId() {
    return BufferUtil.bufferAsString(historyItemIdProp.getValue());
  }

  public AgentHistoryRecord setHistoryItemId(final String historyItemId) {
    historyItemIdProp.setValue(historyItemId);
    return this;
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

  public AgentHistoryRecord setTools(final List<? extends AgentInstanceToolValue> tools) {
    toolsProp.reset();
    if (tools != null) {
      for (final var tool : tools) {
        toolsProp.add().copy(tool);
      }
    }
    return this;
  }

  @Override
  public String getModel() {
    return BufferUtil.bufferAsString(modelProp.getValue());
  }

  public AgentHistoryRecord setModel(final String model) {
    modelProp.setValue(model);
    return this;
  }

  @Override
  public String getProvider() {
    return BufferUtil.bufferAsString(providerProp.getValue());
  }

  public AgentHistoryRecord setProvider(final String provider) {
    providerProp.setValue(provider);
    return this;
  }

  @Override
  public AgentInstanceLimits getLimits() {
    return limitsProp.getValue();
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributesProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public AgentHistoryRecord setChangedAttributes(final List<String> changedAttributes) {
    changedAttributesProp.reset();
    if (changedAttributes != null) {
      changedAttributes.forEach(
          attr -> changedAttributesProp.add().wrap(BufferUtil.wrapString(attr)));
    }
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public AgentHistoryRecord setDuplicate(final boolean duplicate) {
    isDuplicateProp.setValue(duplicate);
    return this;
  }

  public AgentHistoryRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProp.getValue();
  }

  public AgentHistoryRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProp.setValue(storageOrdinalKey);
    return this;
  }

  public AgentHistoryRecord addContent(final AgentHistoryMessageContent content) {
    contentProp.add().copy(content);
    return this;
  }

  public AgentHistoryRecord addSystemPrompt(final AgentHistoryMessageContent block) {
    systemPromptProp.add().copy(block);
    return this;
  }

  public AgentHistoryRecord addToolCall(final AgentHistoryEmbeddedToolCall toolCall) {
    toolCallsProp.add().copy(toolCall);
    return this;
  }

  public AgentHistoryRecord ignoreLease() {
    return setJobLease(JobRecord.EMPTY_LEASE);
  }

  public AgentHistoryRecord addChangedAttribute(final String attribute) {
    changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute));
    return this;
  }
}
