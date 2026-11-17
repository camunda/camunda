/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentInstanceLimits extends MsgpackBufferValue<AgentInstanceLimits>
    implements AgentInstanceRecordValue.AgentInstanceLimitsValue {

  long maxTokens = -1L;
  int maxModelCalls = -1;
  int maxToolCalls = -1;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getMaxTokens() {
    return maxTokens;
  }

  public AgentInstanceLimits setMaxTokens(final long maxTokens) {
    this.maxTokens = maxTokens;
    return this;
  }

  @Override
  public int getMaxModelCalls() {
    return maxModelCalls;
  }

  public AgentInstanceLimits setMaxModelCalls(final int maxModelCalls) {
    this.maxModelCalls = maxModelCalls;
    return this;
  }

  @Override
  public int getMaxToolCalls() {
    return maxToolCalls;
  }

  public AgentInstanceLimits setMaxToolCalls(final int maxToolCalls) {
    this.maxToolCalls = maxToolCalls;
    return this;
  }

  @Override
  protected MsgpackCodec<AgentInstanceLimits> codec() {
    return AgentInstanceLimitsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentInstanceLimits> ops() {
    return AgentInstanceLimitsOps.INSTANCE;
  }
}
