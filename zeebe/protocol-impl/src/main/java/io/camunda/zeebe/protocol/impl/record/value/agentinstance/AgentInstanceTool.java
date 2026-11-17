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
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentInstanceTool extends MsgpackBufferValue<AgentInstanceTool>
    implements AgentInstanceToolValue {

  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer description = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  public AgentInstanceTool setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @Override
  public String getDescription() {
    return BufferUtil.bufferAsString(description);
  }

  public AgentInstanceTool setDescription(final String description) {
    this.description = BufferUtil.wrapString(description);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public AgentInstanceTool setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public void copy(final AgentInstanceToolValue other) {
    setName(other.getName());
    setDescription(other.getDescription());
    setElementId(other.getElementId());
  }

  @Override
  protected MsgpackCodec<AgentInstanceTool> codec() {
    return AgentInstanceToolCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentInstanceTool> ops() {
    return AgentInstanceToolOps.INSTANCE;
  }
}
