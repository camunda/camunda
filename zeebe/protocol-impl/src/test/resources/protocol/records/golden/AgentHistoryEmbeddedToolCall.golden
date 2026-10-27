/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentHistoryEmbeddedToolCall extends ObjectValue
    implements AgentHistoryEmbeddedToolCallValue {

  private final StringProperty toolCallIdProp = new StringProperty("toolCallId", "");
  private final StringProperty toolNameProp = new StringProperty("toolName", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final DocumentProperty argumentsProp = new DocumentProperty("arguments");

  public AgentHistoryEmbeddedToolCall() {
    super(4);
    declareProperty(toolCallIdProp)
        .declareProperty(toolNameProp)
        .declareProperty(elementIdProp)
        .declareProperty(argumentsProp);
  }

  @Override
  public String getToolCallId() {
    return BufferUtil.bufferAsString(toolCallIdProp.getValue());
  }

  public AgentHistoryEmbeddedToolCall setToolCallId(final String toolCallId) {
    toolCallIdProp.setValue(toolCallId);
    return this;
  }

  @Override
  public String getToolName() {
    return BufferUtil.bufferAsString(toolNameProp.getValue());
  }

  public AgentHistoryEmbeddedToolCall setToolName(final String toolName) {
    toolNameProp.setValue(toolName);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentHistoryEmbeddedToolCall setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public Map<String, Object> getArguments() {
    return MsgPackConverter.convertToMap(argumentsProp.getValue());
  }

  public AgentHistoryEmbeddedToolCall setArguments(final DirectBuffer arguments) {
    argumentsProp.setValue(arguments);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getArgumentsBuffer() {
    return argumentsProp.getValue();
  }

  public void copy(final AgentHistoryEmbeddedToolCallValue other) {
    setToolCallId(other.getToolCallId());
    setToolName(other.getToolName());
    setElementId(other.getElementId());
    setArguments(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(other.getArguments())));
  }
}
