/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMetricsValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentHistoryMetrics extends MsgpackBufferValue<AgentHistoryMetrics>
    implements AgentHistoryMetricsValue {

  long inputTokens = -1L;
  long outputTokens = -1L;
  long reasoningTokenCount = -1L;
  long cacheCreationTokenCount = -1L;
  long cacheReadTokenCount = -1L;
  long durationMs = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getInputTokens() {
    return inputTokens;
  }

  public AgentHistoryMetrics setInputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  @Override
  public long getOutputTokens() {
    return outputTokens;
  }

  public AgentHistoryMetrics setOutputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  @Override
  public long getReasoningTokenCount() {
    return reasoningTokenCount;
  }

  public AgentHistoryMetrics setReasoningTokenCount(final long reasoningTokenCount) {
    this.reasoningTokenCount = reasoningTokenCount;
    return this;
  }

  @Override
  public long getCacheCreationTokenCount() {
    return cacheCreationTokenCount;
  }

  public AgentHistoryMetrics setCacheCreationTokenCount(final long cacheCreationTokenCount) {
    this.cacheCreationTokenCount = cacheCreationTokenCount;
    return this;
  }

  @Override
  public long getCacheReadTokenCount() {
    return cacheReadTokenCount;
  }

  public AgentHistoryMetrics setCacheReadTokenCount(final long cacheReadTokenCount) {
    this.cacheReadTokenCount = cacheReadTokenCount;
    return this;
  }

  @Override
  public long getDurationMs() {
    return durationMs;
  }

  public AgentHistoryMetrics setDurationMs(final long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  @Override
  protected MsgpackCodec<AgentHistoryMetrics> codec() {
    return AgentHistoryMetricsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentHistoryMetrics> ops() {
    return AgentHistoryMetricsOps.INSTANCE;
  }
}
