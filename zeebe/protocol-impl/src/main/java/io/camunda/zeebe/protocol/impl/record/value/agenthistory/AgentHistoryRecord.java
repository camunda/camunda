/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryCommitStatus;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AgentHistoryRecord extends UnifiedRecordValue
    implements AgentHistoryRecordValue {

  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final IntegerProperty attemptNumberProp = new IntegerProperty("attemptNumber", 0);
  private final IntegerProperty iterationProp = new IntegerProperty("iteration", 0);
  private final EnumProperty<AgentHistoryRole> roleProp =
      new EnumProperty<>("role", AgentHistoryRole.class, AgentHistoryRole.UNSPECIFIED);
  private final EnumProperty<AgentHistoryCommitStatus> commitStatusProp =
      new EnumProperty<>(
          "commitStatus", AgentHistoryCommitStatus.class, AgentHistoryCommitStatus.UNSPECIFIED);
  private final LongProperty producedAtProp = new LongProperty("producedAt", -1L);
  private final StringProperty metadataProp = new StringProperty("metadata", "");
  private final ArrayProperty<AgentHistoryMessageContent> contentProp =
      new ArrayProperty<>("content", AgentHistoryMessageContent::new);
  private final ArrayProperty<AgentHistoryEmbeddedToolCall> toolCallsProp =
      new ArrayProperty<>("toolCalls", AgentHistoryEmbeddedToolCall::new);
  private final ObjectProperty<AgentHistoryToolCallRef> toolCallRefProp =
      new ObjectProperty<>("toolCallRef", new AgentHistoryToolCallRef());
  private final ObjectProperty<AgentHistoryMetrics> metricsProp =
      new ObjectProperty<>("metrics", new AgentHistoryMetrics());

  public AgentHistoryRecord() {
    super(13);
    declareProperty(agentInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp)
        .declareProperty(attemptNumberProp)
        .declareProperty(iterationProp)
        .declareProperty(roleProp)
        .declareProperty(commitStatusProp)
        .declareProperty(producedAtProp)
        .declareProperty(metadataProp)
        .declareProperty(contentProp)
        .declareProperty(toolCallsProp)
        .declareProperty(toolCallRefProp)
        .declareProperty(metricsProp);
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
  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public AgentHistoryRecord setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
    return this;
  }

  @Override
  public int getAttemptNumber() {
    return attemptNumberProp.getValue();
  }

  public AgentHistoryRecord setAttemptNumber(final int attemptNumber) {
    attemptNumberProp.setValue(attemptNumber);
    return this;
  }

  @Override
  public int getIteration() {
    return iterationProp.getValue();
  }

  public AgentHistoryRecord setIteration(final int iteration) {
    iterationProp.setValue(iteration);
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
  public AgentHistoryCommitStatus getCommitStatus() {
    return commitStatusProp.getValue();
  }

  public AgentHistoryRecord setCommitStatus(final AgentHistoryCommitStatus commitStatus) {
    commitStatusProp.setValue(commitStatus);
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
  public String getMetadata() {
    return BufferUtil.bufferAsString(metadataProp.getValue());
  }

  public AgentHistoryRecord setMetadata(final String metadata) {
    metadataProp.setValue(metadata);
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

  public AgentHistoryRecord addContent(final AgentHistoryMessageContent content) {
    contentProp.add().copy(content);
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

  public AgentHistoryRecord addToolCall(final AgentHistoryEmbeddedToolCall toolCall) {
    toolCallsProp.add().copy(toolCall);
    return this;
  }

  @Override
  public AgentHistoryToolCallRef getToolCallRef() {
    return toolCallRefProp.getValue();
  }

  @Override
  public AgentHistoryMetrics getMetrics() {
    return metricsProp.getValue();
  }
}
