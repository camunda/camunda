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
public final class AgentInstanceMetrics extends MsgpackBufferValue<AgentInstanceMetrics>
    implements AgentInstanceRecordValue.AgentInstanceMetricsValue {

  long inputTokens = 0L;
  long outputTokens = 0L;
  long reasoningTokenCount = 0L;
  long cacheCreationTokenCount = 0L;
  long cacheReadTokenCount = 0L;
  int modelCalls = 0;
  int toolCalls = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getInputTokens() {
    return inputTokens;
  }

  public AgentInstanceMetrics setInputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  @Override
  public long getOutputTokens() {
    return outputTokens;
  }

  public AgentInstanceMetrics setOutputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  @Override
  public long getReasoningTokenCount() {
    return reasoningTokenCount;
  }

  public AgentInstanceMetrics setReasoningTokenCount(final long reasoningTokenCount) {
    this.reasoningTokenCount = reasoningTokenCount;
    return this;
  }

  @Override
  public long getCacheCreationTokenCount() {
    return cacheCreationTokenCount;
  }

  public AgentInstanceMetrics setCacheCreationTokenCount(final long cacheCreationTokenCount) {
    this.cacheCreationTokenCount = cacheCreationTokenCount;
    return this;
  }

  @Override
  public long getCacheReadTokenCount() {
    return cacheReadTokenCount;
  }

  public AgentInstanceMetrics setCacheReadTokenCount(final long cacheReadTokenCount) {
    this.cacheReadTokenCount = cacheReadTokenCount;
    return this;
  }

  @Override
  public int getModelCalls() {
    return modelCalls;
  }

  public AgentInstanceMetrics setModelCalls(final int modelCalls) {
    this.modelCalls = modelCalls;
    return this;
  }

  @Override
  public int getToolCalls() {
    return toolCalls;
  }

  public AgentInstanceMetrics setToolCalls(final int toolCalls) {
    this.toolCalls = toolCalls;
    return this;
  }

  @Override
  protected MsgpackCodec<AgentInstanceMetrics> codec() {
    return AgentInstanceMetricsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentInstanceMetrics> ops() {
    return AgentInstanceMetricsOps.INSTANCE;
  }
}
