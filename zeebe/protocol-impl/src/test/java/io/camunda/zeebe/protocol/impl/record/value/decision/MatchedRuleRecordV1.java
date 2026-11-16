/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class MatchedRuleRecordV1 extends UnifiedRecordValue implements MatchedRuleValue {

  private final StringProperty ruleIdProp = new StringProperty("ruleId");
  private final IntegerProperty ruleIndexProp = new IntegerProperty("ruleIndex");

  private final ArrayProperty<EvaluatedOutputRecordV1> evaluatedOutputsProp =
      new ArrayProperty<>("evaluatedOutputs", EvaluatedOutputRecordV1::new);

  public MatchedRuleRecordV1() {
    super(3);
    declareProperty(ruleIdProp)
        .declareProperty(ruleIndexProp)
        .declareProperty(evaluatedOutputsProp);
  }

  @Override
  public String getRuleId() {
    return bufferAsString(ruleIdProp.getValue());
  }

  public MatchedRuleRecordV1 setRuleId(final String ruleId) {
    ruleIdProp.setValue(ruleId);
    return this;
  }

  @Override
  public int getRuleIndex() {
    return ruleIndexProp.getValue();
  }

  public MatchedRuleRecordV1 setRuleIndex(final int ruleIndex) {
    ruleIndexProp.setValue(ruleIndex);
    return this;
  }

  @Override
  public List<EvaluatedOutputValue> getEvaluatedOutputs() {
    final List<EvaluatedOutputValue> evaluatedOutputs = new ArrayList<>();

    for (final EvaluatedOutputRecordV1 evaluatedOutput : evaluatedOutputsProp) {
      final var copyRecord = new EvaluatedOutputRecordV1();
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
  public ValueArray<EvaluatedOutputRecordV1> evaluatedOutputs() {
    return evaluatedOutputsProp;
  }
}
