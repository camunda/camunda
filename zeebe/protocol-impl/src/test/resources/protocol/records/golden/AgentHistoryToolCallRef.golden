/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryToolCallRefValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentHistoryToolCallRef extends ObjectValue
    implements AgentHistoryToolCallRefValue {

  private final StringProperty toolCallIdProp = new StringProperty("toolCallId", "");
  private final StringProperty toolNameProp = new StringProperty("toolName", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty toolElementInstanceKeyProp =
      new LongProperty("toolElementInstanceKey", -1L);

  public AgentHistoryToolCallRef() {
    super(4);
    declareProperty(toolCallIdProp)
        .declareProperty(toolNameProp)
        .declareProperty(elementIdProp)
        .declareProperty(toolElementInstanceKeyProp);
  }

  @Override
  public String getToolCallId() {
    return BufferUtil.bufferAsString(toolCallIdProp.getValue());
  }

  public AgentHistoryToolCallRef setToolCallId(final String toolCallId) {
    toolCallIdProp.setValue(toolCallId);
    return this;
  }

  @Override
  public String getToolName() {
    return BufferUtil.bufferAsString(toolNameProp.getValue());
  }

  public AgentHistoryToolCallRef setToolName(final String toolName) {
    toolNameProp.setValue(toolName);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentHistoryToolCallRef setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public long getToolElementInstanceKey() {
    return toolElementInstanceKeyProp.getValue();
  }

  public AgentHistoryToolCallRef setToolElementInstanceKey(final long toolElementInstanceKey) {
    toolElementInstanceKeyProp.setValue(toolElementInstanceKey);
    return this;
  }

  public void copy(final AgentHistoryToolCallRefValue other) {
    setToolCallId(other.getToolCallId());
    setToolName(other.getToolName());
    setElementId(other.getElementId());
    setToolElementInstanceKey(other.getToolElementInstanceKey());
  }
}
