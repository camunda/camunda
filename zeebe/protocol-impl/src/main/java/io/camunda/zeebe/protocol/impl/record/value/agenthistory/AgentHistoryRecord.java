/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceLimits;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AgentHistoryRecord extends MsgpackBufferValue<AgentHistoryRecord>
    implements MsgpackRecordValue, AgentHistoryRecordValue {

  long agentHistoryKey = -1L;
  long agentInstanceKey = -1L;
  long elementInstanceKey = -1L;
  long processInstanceKey = -1L;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long jobKey = -1L;
  DirectBuffer jobLeaseToken = Decoding.EMPTY;
  int loopIteration = 0;
  AgentHistoryRole role = AgentHistoryRole.UNSPECIFIED;
  long producedAt = -1L;
  final List<AgentHistoryMessageContent> content = new ArrayList<>();
  final List<AgentHistoryMessageContent> systemPrompt = new ArrayList<>();
  final List<AgentHistoryEmbeddedToolCall> toolCalls = new ArrayList<>();
  final AgentHistoryMetrics metrics = new AgentHistoryMetrics();
  DirectBuffer historyItemId = Decoding.EMPTY;
  final List<AgentInstanceTool> tools = new ArrayList<>();
  DirectBuffer model = Decoding.EMPTY;
  DirectBuffer provider = Decoding.EMPTY;
  final AgentInstanceLimits limits = new AgentInstanceLimits();
  final List<DirectBuffer> changedAttributes = new ArrayList<>();
  boolean isDuplicate = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getAgentHistoryKey() {
    return agentHistoryKey;
  }

  public AgentHistoryRecord setAgentHistoryKey(final long agentHistoryKey) {
    this.agentHistoryKey = agentHistoryKey;
    return this;
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public AgentHistoryRecord setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public AgentHistoryRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AgentHistoryRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public AgentHistoryRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentHistoryRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public AgentHistoryRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  public AgentHistoryRecord setJobKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  @Override
  public String getJobLeaseToken() {
    return BufferUtil.bufferAsString(jobLeaseToken);
  }

  public AgentHistoryRecord setJobLeaseToken(final String jobLeaseToken) {
    this.jobLeaseToken = BufferUtil.wrapString(jobLeaseToken);
    return this;
  }

  @Override
  public int getLoopIteration() {
    return loopIteration;
  }

  public AgentHistoryRecord setLoopIteration(final int loopIteration) {
    this.loopIteration = loopIteration;
    return this;
  }

  @Override
  public AgentHistoryRole getRole() {
    return role;
  }

  public AgentHistoryRecord setRole(final AgentHistoryRole role) {
    this.role = role;
    return this;
  }

  @Override
  public long getProducedAt() {
    return producedAt;
  }

  public AgentHistoryRecord setProducedAt(final long producedAt) {
    this.producedAt = producedAt;
    return this;
  }

  @Override
  public List<AgentHistoryMessageContentValue> getContent() {
    return content.stream()
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
    this.content.clear();
    for (final var item : content) {
      final var copy = new AgentHistoryMessageContent();
      copy.copy(item);
      this.content.add(copy);
    }
    return this;
  }

  @Override
  public List<AgentHistoryMessageContentValue> getSystemPrompt() {
    return systemPrompt.stream()
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
    this.systemPrompt.clear();
    if (systemPrompt != null) {
      for (final var item : systemPrompt) {
        final var copy = new AgentHistoryMessageContent();
        copy.copy(item);
        this.systemPrompt.add(copy);
      }
    }
    return this;
  }

  @Override
  public List<AgentHistoryEmbeddedToolCallValue> getToolCalls() {
    return toolCalls.stream()
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
    this.toolCalls.clear();
    for (final var item : toolCalls) {
      final var copy = new AgentHistoryEmbeddedToolCall();
      copy.copy(item);
      this.toolCalls.add(copy);
    }
    return this;
  }

  @Override
  public AgentHistoryMetrics getMetrics() {
    return metrics;
  }

  @Override
  public String getHistoryItemId() {
    return BufferUtil.bufferAsString(historyItemId);
  }

  public AgentHistoryRecord setHistoryItemId(final String historyItemId) {
    this.historyItemId = BufferUtil.wrapString(historyItemId);
    return this;
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

  public AgentHistoryRecord setTools(final List<? extends AgentInstanceToolValue> tools) {
    this.tools.clear();
    if (tools != null) {
      for (final var tool : tools) {
        final var copy = new AgentInstanceTool();
        copy.copy(tool);
        this.tools.add(copy);
      }
    }
    return this;
  }

  @Override
  public String getModel() {
    return BufferUtil.bufferAsString(model);
  }

  public AgentHistoryRecord setModel(final String model) {
    this.model = BufferUtil.wrapString(model);
    return this;
  }

  @Override
  public String getProvider() {
    return BufferUtil.bufferAsString(provider);
  }

  public AgentHistoryRecord setProvider(final String provider) {
    this.provider = BufferUtil.wrapString(provider);
    return this;
  }

  @Override
  public AgentInstanceLimits getLimits() {
    return limits;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes.stream().map(BufferUtil::bufferAsString).toList();
  }

  public AgentHistoryRecord setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes.clear();
    if (changedAttributes != null) {
      changedAttributes.forEach(attr -> this.changedAttributes.add(BufferUtil.wrapString(attr)));
    }
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public AgentHistoryRecord setDuplicate(final boolean duplicate) {
    this.isDuplicate = duplicate;
    return this;
  }

  public AgentHistoryRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public AgentHistoryRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public AgentHistoryRecord addContent(final AgentHistoryMessageContent content) {
    final var copy = new AgentHistoryMessageContent();
    copy.copy(content);
    this.content.add(copy);
    return this;
  }

  public AgentHistoryRecord addSystemPrompt(final AgentHistoryMessageContent block) {
    final var copy = new AgentHistoryMessageContent();
    copy.copy(block);
    systemPrompt.add(copy);
    return this;
  }

  public AgentHistoryRecord addToolCall(final AgentHistoryEmbeddedToolCall toolCall) {
    final var copy = new AgentHistoryEmbeddedToolCall();
    copy.copy(toolCall);
    toolCalls.add(copy);
    return this;
  }

  public AgentHistoryRecord ignoreLease() {
    return setJobLeaseToken(JobRecord.EMPTY_JOB_LEASE_TOKEN);
  }

  public AgentHistoryRecord addChangedAttribute(final String attribute) {
    changedAttributes.add(BufferUtil.wrapString(attribute));
    return this;
  }

  @Override
  protected MsgpackCodec<AgentHistoryRecord> codec() {
    return AgentHistoryRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentHistoryRecord> ops() {
    return AgentHistoryRecordOps.INSTANCE;
  }
}
