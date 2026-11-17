/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentHistoryEmbeddedToolCall
    extends MsgpackBufferValue<AgentHistoryEmbeddedToolCall>
    implements AgentHistoryEmbeddedToolCallValue {

  DirectBuffer toolCallId = Decoding.EMPTY;
  DirectBuffer toolName = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer arguments = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getToolCallId() {
    return BufferUtil.bufferAsString(toolCallId);
  }

  public AgentHistoryEmbeddedToolCall setToolCallId(final String toolCallId) {
    this.toolCallId = BufferUtil.wrapString(toolCallId);
    return this;
  }

  @Override
  public String getToolName() {
    return BufferUtil.bufferAsString(toolName);
  }

  public AgentHistoryEmbeddedToolCall setToolName(final String toolName) {
    this.toolName = BufferUtil.wrapString(toolName);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public AgentHistoryEmbeddedToolCall setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  @Override
  public Map<String, Object> getArguments() {
    return MsgPackConverter.convertToMap(arguments);
  }

  public AgentHistoryEmbeddedToolCall setArguments(final Map<String, Object> arguments) {
    this.arguments =
        Decoding.document(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(arguments)));
    return this;
  }

  public void copy(final AgentHistoryEmbeddedToolCallValue other) {
    setToolCallId(other.getToolCallId());
    setToolName(other.getToolName());
    setElementId(other.getElementId());
    setArguments(other.getArguments());
  }

  @Override
  protected MsgpackCodec<AgentHistoryEmbeddedToolCall> codec() {
    return AgentHistoryEmbeddedToolCallCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentHistoryEmbeddedToolCall> ops() {
    return AgentHistoryEmbeddedToolCallOps.INSTANCE;
  }
}
