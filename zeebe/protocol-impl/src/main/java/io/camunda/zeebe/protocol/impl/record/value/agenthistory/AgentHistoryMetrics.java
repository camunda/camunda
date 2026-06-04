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
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMetricsValue;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentHistoryMetrics extends ObjectValue implements AgentHistoryMetricsValue {

  private final LongProperty inputTokensProp = new LongProperty("inputTokens", 0L);
  private final LongProperty outputTokensProp = new LongProperty("outputTokens", 0L);
  private final LongProperty durationMsProp = new LongProperty("durationMs", 0L);

  public AgentHistoryMetrics() {
    super(3);
    declareProperty(inputTokensProp)
        .declareProperty(outputTokensProp)
        .declareProperty(durationMsProp);
  }

  @Override
  public long getInputTokens() {
    return inputTokensProp.getValue();
  }

  public AgentHistoryMetrics setInputTokens(final long inputTokens) {
    inputTokensProp.setValue(inputTokens);
    return this;
  }

  @Override
  public long getOutputTokens() {
    return outputTokensProp.getValue();
  }

  public AgentHistoryMetrics setOutputTokens(final long outputTokens) {
    outputTokensProp.setValue(outputTokens);
    return this;
  }

  @Override
  public long getDurationMs() {
    return durationMsProp.getValue();
  }

  public AgentHistoryMetrics setDurationMs(final long durationMs) {
    durationMsProp.setValue(durationMs);
    return this;
  }
}
