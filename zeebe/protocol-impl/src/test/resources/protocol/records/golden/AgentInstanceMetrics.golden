/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentInstanceMetrics extends ObjectValue
    implements AgentInstanceRecordValue.AgentInstanceMetricsValue {

  private final LongProperty inputTokensProp = new LongProperty("inputTokens", 0L);
  private final LongProperty outputTokensProp = new LongProperty("outputTokens", 0L);
  private final IntegerProperty modelCallsProp = new IntegerProperty("modelCalls", 0);
  private final IntegerProperty toolCallsProp = new IntegerProperty("toolCalls", 0);

  public AgentInstanceMetrics() {
    super(4);
    declareProperty(inputTokensProp)
        .declareProperty(outputTokensProp)
        .declareProperty(modelCallsProp)
        .declareProperty(toolCallsProp);
  }

  @Override
  public long getInputTokens() {
    return inputTokensProp.getValue();
  }

  public AgentInstanceMetrics setInputTokens(final long inputTokens) {
    inputTokensProp.setValue(inputTokens);
    return this;
  }

  @Override
  public long getOutputTokens() {
    return outputTokensProp.getValue();
  }

  public AgentInstanceMetrics setOutputTokens(final long outputTokens) {
    outputTokensProp.setValue(outputTokens);
    return this;
  }

  @Override
  public int getModelCalls() {
    return modelCallsProp.getValue();
  }

  public AgentInstanceMetrics setModelCalls(final int modelCalls) {
    modelCallsProp.setValue(modelCalls);
    return this;
  }

  @Override
  public int getToolCalls() {
    return toolCallsProp.getValue();
  }

  public AgentInstanceMetrics setToolCalls(final int toolCalls) {
    toolCallsProp.setValue(toolCalls);
    return this;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
