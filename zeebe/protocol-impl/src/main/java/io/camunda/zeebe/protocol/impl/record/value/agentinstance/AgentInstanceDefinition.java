/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentInstanceDefinition extends MsgpackBufferValue<AgentInstanceDefinition>
    implements AgentInstanceRecordValue.AgentInstanceDefinitionValue {

  DirectBuffer model = Decoding.EMPTY;
  DirectBuffer provider = Decoding.EMPTY;
  final List<AgentHistoryMessageContent> systemPrompt = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getModel() {
    return BufferUtil.bufferAsString(model);
  }

  public AgentInstanceDefinition setModel(final String model) {
    this.model = BufferUtil.wrapString(model);
    return this;
  }

  @Override
  public String getProvider() {
    return BufferUtil.bufferAsString(provider);
  }

  public AgentInstanceDefinition setProvider(final String provider) {
    this.provider = BufferUtil.wrapString(provider);
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

  public AgentInstanceDefinition setSystemPrompt(
      final List<? extends AgentHistoryMessageContentValue> systemPrompt) {
    this.systemPrompt.clear();
    if (systemPrompt != null) {
      for (final var item : systemPrompt) {
        copyIntoSystemPrompt(item);
      }
    }
    return this;
  }

  public AgentInstanceDefinition addSystemPrompt(final AgentHistoryMessageContent block) {
    copyIntoSystemPrompt(block);
    return this;
  }

  private void copyIntoSystemPrompt(final AgentHistoryMessageContentValue block) {
    final var copy = new AgentHistoryMessageContent();
    copy.copy(block);
    systemPrompt.add(copy);
  }

  @Override
  protected MsgpackCodec<AgentInstanceDefinition> codec() {
    return AgentInstanceDefinitionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentInstanceDefinition> ops() {
    return AgentInstanceDefinitionOps.INSTANCE;
  }
}
