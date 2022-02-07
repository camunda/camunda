/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

public final class MatchedRuleRecord extends UnifiedRecordValue implements MatchedRuleValue {

  private final StringProperty ruleIdProp = new StringProperty("ruleId");
  private final IntegerProperty ruleIndexProp = new IntegerProperty("ruleIndex");

  private final ArrayProperty<EvaluatedOutputRecord> evaluatedOutputsProp =
      new ArrayProperty<>("evaluatedOutputs", new EvaluatedOutputRecord());

  public MatchedRuleRecord() {
    declareProperty(ruleIdProp)
        .declareProperty(ruleIndexProp)
        .declareProperty(evaluatedOutputsProp);
  }

  @Override
  public String getRuleId() {
    return bufferAsString(ruleIdProp.getValue());
  }

  public MatchedRuleRecord setRuleId(final String ruleId) {
    ruleIdProp.setValue(ruleId);
    return this;
  }

  @Override
  public int getRuleIndex() {
    return ruleIndexProp.getValue();
  }

  public MatchedRuleRecord setRuleIndex(final int ruleIndex) {
    ruleIndexProp.setValue(ruleIndex);
    return this;
  }

  @Override
  public List<EvaluatedOutputValue> getEvaluatedOutputs() {
    final List<EvaluatedOutputValue> evaluatedOutputs = new ArrayList<>();

    for (final EvaluatedOutputRecord evaluatedOutput : evaluatedOutputsProp) {
      final var copyRecord = new EvaluatedOutputRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedOutput);
      copyRecord.wrap(copyBuffer);
      evaluatedOutputs.add(copyRecord);
    }

    return evaluatedOutputs;
  }

  @JsonIgnore
  public DirectBuffer getRuleIdBuffer() {
    return ruleIdProp.getValue();
  }

  @JsonIgnore
  public ValueArray<EvaluatedOutputRecord> evaluatedOutputs() {
    return evaluatedOutputsProp;
  }
}
