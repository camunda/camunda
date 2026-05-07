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
public final class AgentInstanceLimits extends ObjectValue
    implements AgentInstanceRecordValue.AgentInstanceLimitsValue {

  private final LongProperty maxTokensProp = new LongProperty("maxTokens", -1L);
  private final IntegerProperty maxModelCallsProp = new IntegerProperty("maxModelCalls", -1);
  private final IntegerProperty maxToolCallsProp = new IntegerProperty("maxToolCalls", -1);

  public AgentInstanceLimits() {
    super(3);
    declareProperty(maxTokensProp)
        .declareProperty(maxModelCallsProp)
        .declareProperty(maxToolCallsProp);
  }

  @Override
  public long getMaxTokens() {
    return maxTokensProp.getValue();
  }

  public AgentInstanceLimits setMaxTokens(final long maxTokens) {
    maxTokensProp.setValue(maxTokens);
    return this;
  }

  @Override
  public int getMaxModelCalls() {
    return maxModelCallsProp.getValue();
  }

  public AgentInstanceLimits setMaxModelCalls(final int maxModelCalls) {
    maxModelCallsProp.setValue(maxModelCalls);
    return this;
  }

  @Override
  public int getMaxToolCalls() {
    return maxToolCallsProp.getValue();
  }

  public AgentInstanceLimits setMaxToolCalls(final int maxToolCalls) {
    maxToolCallsProp.setValue(maxToolCalls);
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
