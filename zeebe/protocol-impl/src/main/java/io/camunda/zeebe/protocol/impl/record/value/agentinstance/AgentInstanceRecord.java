/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AgentInstanceRecord extends MsgpackBufferValue<AgentInstanceRecord>
    implements MsgpackRecordValue, AgentInstanceRecordValue {

  long agentInstanceKey = -1L;
  long agentDefinitionKey = -1L;
  long elementInstanceKey = -1L;
  final LongArrayList elementInstanceKeys = new LongArrayList();
  DirectBuffer elementId = Decoding.EMPTY;
  long processInstanceKey = -1L;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  int processDefinitionVersion = -1;
  DirectBuffer processDefinitionVersionTag = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  AgentInstanceStatus status = AgentInstanceStatus.UNSPECIFIED;
  final AgentInstanceDefinition definition = new AgentInstanceDefinition();
  final AgentInstanceLimits limits = new AgentInstanceLimits();
  final AgentInstanceMetrics metrics = new AgentInstanceMetrics();
  final List<AgentInstanceTool> tools = new ArrayList<>();
  final List<DirectBuffer> changedAttributes = new ArrayList<>();
  long jobKey = -1L;
  DirectBuffer jobLeaseToken = Decoding.EMPTY;
  final List<AgentHistoryRecord> history = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public AgentInstanceRecord setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
    return this;
  }

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKey;
  }

  public AgentInstanceRecord setAgentDefinitionKey(final long agentDefinitionKey) {
    this.agentDefinitionKey = agentDefinitionKey;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public AgentInstanceRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeys.stream().toList();
  }

  public AgentInstanceRecord setElementInstanceKeys(final List<Long> elementInstanceKeys) {
    this.elementInstanceKeys.clear();
    if (elementInstanceKeys != null) {
      for (final long key : elementInstanceKeys) {
        this.elementInstanceKeys.addLong(key);
      }
    }
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public AgentInstanceRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AgentInstanceRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public AgentInstanceRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentInstanceRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public AgentInstanceRecord setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return BufferUtil.bufferAsString(processDefinitionVersionTag);
  }

  public AgentInstanceRecord setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = BufferUtil.wrapString(processDefinitionVersionTag);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public AgentInstanceRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return status;
  }

  public AgentInstanceRecord setStatus(final AgentInstanceStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public AgentInstanceDefinition getDefinition() {
    return definition;
  }

  @Override
  public AgentInstanceLimits getLimits() {
    return limits;
  }

  @Override
  public AgentInstanceMetrics getMetrics() {
    return metrics;
  }

  @Override
  public List<AgentInstanceToolValue> getTools() {
    return tools.stream()
        .map(
            element -> {
              final var copy = new AgentInstanceTool();
              copy.copy(element);
              return (AgentInstanceToolValue) copy;
            })
        .toList();
  }

  public AgentInstanceRecord setTools(final List<? extends AgentInstanceToolValue> tools) {
    this.tools.clear();
    for (final var tool : tools) {
      final var copy = new AgentInstanceTool();
      copy.copy(tool);
      this.tools.add(copy);
    }
    return this;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes.stream().map(BufferUtil::bufferAsString).toList();
  }

  public AgentInstanceRecord setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes.clear();
    if (changedAttributes != null) {
      changedAttributes.forEach(attr -> this.changedAttributes.add(BufferUtil.wrapString(attr)));
    }
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  public AgentInstanceRecord setJobKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  @Override
  public String getJobLeaseToken() {
    return BufferUtil.bufferAsString(jobLeaseToken);
  }

  public AgentInstanceRecord setJobLeaseToken(final String jobLeaseToken) {
    this.jobLeaseToken = BufferUtil.wrapString(jobLeaseToken);
    return this;
  }

  @Override
  public List<AgentHistoryRecordValue> getHistory() {
    return history.stream().collect(Collectors.toList());
  }

  public AgentInstanceRecord setHistory(final List<? extends AgentHistoryRecord> history) {
    this.history.clear();
    if (history != null) {
      for (final var item : history) {
        final var copy = new AgentHistoryRecord();
        copy.copyFrom(item);
        this.history.add(copy);
      }
    }
    return this;
  }

  public AgentInstanceRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public AgentInstanceRecord addElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeys.addLong(elementInstanceKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public AgentInstanceRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public AgentInstanceRecord addChangedAttribute(final String attribute) {
    changedAttributes.add(BufferUtil.wrapString(attribute));
    return this;
  }

  public AgentInstanceRecord addHistoryItem(final AgentHistoryRecord historyItem) {
    final var copy = new AgentHistoryRecord();
    copy.copyFrom(historyItem);
    history.add(copy);
    return this;
  }

  @Override
  protected MsgpackCodec<AgentInstanceRecord> codec() {
    return AgentInstanceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentInstanceRecord> ops() {
    return AgentInstanceRecordOps.INSTANCE;
  }
}
