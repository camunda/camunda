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
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

public final class EvaluatedDecisionRecord extends UnifiedRecordValue
    implements EvaluatedDecisionValue {

  private final StringProperty decisionIdProp = new StringProperty("decisionId");
  private final StringProperty decisionNameProp = new StringProperty("decisionName");
  private final LongProperty decisionKeyProp = new LongProperty("decisionKey");
  private final IntegerProperty decisionVersionProp = new IntegerProperty("decisionVersion");
  private final StringProperty decisionTypeProp = new StringProperty("decisionType");
  private final BinaryProperty decisionOutputProp = new BinaryProperty("decisionOutput");

  private final ArrayProperty<EvaluatedInputRecord> evaluatedInputsProp =
      new ArrayProperty<>("evaluatedInputs", new EvaluatedInputRecord());

  private final ArrayProperty<MatchedRuleRecord> matchedRulesProp =
      new ArrayProperty<>("matchedRules", new MatchedRuleRecord());

  public EvaluatedDecisionRecord() {
    declareProperty(decisionIdProp)
        .declareProperty(decisionNameProp)
        .declareProperty(decisionKeyProp)
        .declareProperty(decisionVersionProp)
        .declareProperty(decisionTypeProp)
        .declareProperty(decisionOutputProp)
        .declareProperty(evaluatedInputsProp)
        .declareProperty(matchedRulesProp);
  }

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionIdProp.getValue());
  }

  public EvaluatedDecisionRecord setDecisionId(final String decisionId) {
    decisionIdProp.setValue(decisionId);
    return this;
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionNameProp.getValue());
  }

  public EvaluatedDecisionRecord setDecisionName(final String decisionName) {
    decisionNameProp.setValue(decisionName);
    return this;
  }

  @Override
  public long getDecisionKey() {
    return decisionKeyProp.getValue();
  }

  public EvaluatedDecisionRecord setDecisionKey(final long decisionKey) {
    decisionKeyProp.setValue(decisionKey);
    return this;
  }

  @Override
  public long getDecisionVersion() {
    return decisionVersionProp.getValue();
  }

  public EvaluatedDecisionRecord setDecisionVersion(final int decisionVersion) {
    decisionVersionProp.setValue(decisionVersion);
    return this;
  }

  @Override
  public String getDecisionType() {
    return bufferAsString(decisionTypeProp.getValue());
  }

  public EvaluatedDecisionRecord setDecisionType(final String decisionType) {
    decisionTypeProp.setValue(decisionType);
    return this;
  }

  @Override
  public String getDecisionOutput() {
    return MsgPackConverter.convertToJson(decisionOutputProp.getValue());
  }

  public EvaluatedDecisionRecord setDecisionOutput(final DirectBuffer decisionOutput) {
    decisionOutputProp.setValue(decisionOutput);
    return this;
  }

  @Override
  public List<EvaluatedInputValue> getEvaluatedInputs() {
    final List<EvaluatedInputValue> evaluatedInputs = new ArrayList<>();

    for (final EvaluatedInputRecord evaluatedInput : evaluatedInputsProp) {
      final var copyRecord = new EvaluatedInputRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedInput);
      copyRecord.wrap(copyBuffer);
      evaluatedInputs.add(copyRecord);
    }

    return evaluatedInputs;
  }

  @Override
  public List<MatchedRuleValue> getMatchedRules() {
    final List<MatchedRuleValue> matchedRules = new ArrayList<>();

    for (final MatchedRuleRecord matchedRule : matchedRulesProp) {
      final var copyRecord = new MatchedRuleRecord();
      final var copyBuffer = BufferUtil.createCopy(matchedRule);
      copyRecord.wrap(copyBuffer);
      matchedRules.add(copyRecord);
    }

    return matchedRules;
  }

  @JsonIgnore
  public DirectBuffer getDecisionOutputBuffer() {
    return decisionOutputProp.getValue();
  }

  @JsonIgnore
  public ValueArray<MatchedRuleRecord> matchedRules() {
    return matchedRulesProp;
  }

  @JsonIgnore
  public ValueArray<EvaluatedInputRecord> evaluatedInputs() {
    return evaluatedInputsProp;
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return decisionIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return decisionNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionTypeBuffer() {
    return decisionTypeProp.getValue();
  }
}
