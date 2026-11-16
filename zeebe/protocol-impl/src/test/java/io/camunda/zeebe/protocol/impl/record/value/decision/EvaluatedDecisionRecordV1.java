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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class EvaluatedDecisionRecordV1 extends UnifiedRecordValue
    implements EvaluatedDecisionValue {

  private final StringProperty decisionIdProp = new StringProperty("decisionId");
  private final StringProperty decisionEvaluationInstanceKeyProp =
      new StringProperty("decisionEvaluationInstanceKey", "");
  private final StringProperty decisionNameProp = new StringProperty("decisionName");
  private final LongProperty decisionKeyProp = new LongProperty("decisionKey");
  private final IntegerProperty decisionVersionProp = new IntegerProperty("decisionVersion");
  private final StringProperty decisionTypeProp = new StringProperty("decisionType");
  private final BinaryProperty decisionOutputProp = new BinaryProperty("decisionOutput");

  private final ArrayProperty<EvaluatedInputRecordV1> evaluatedInputsProp =
      new ArrayProperty<>("evaluatedInputs", EvaluatedInputRecordV1::new);

  private final ArrayProperty<MatchedRuleRecordV1> matchedRulesProp =
      new ArrayProperty<>("matchedRules", MatchedRuleRecordV1::new);

  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public EvaluatedDecisionRecordV1() {
    super(10);
    declareProperty(decisionIdProp)
        .declareProperty(decisionEvaluationInstanceKeyProp)
        .declareProperty(decisionNameProp)
        .declareProperty(decisionKeyProp)
        .declareProperty(decisionVersionProp)
        .declareProperty(decisionTypeProp)
        .declareProperty(decisionOutputProp)
        .declareProperty(evaluatedInputsProp)
        .declareProperty(matchedRulesProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionIdProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setDecisionId(final String decisionId) {
    decisionIdProp.setValue(decisionId);
    return this;
  }

  @Override
  @JsonIgnore
  public String getDecisionEvaluationInstanceKey() {
    return bufferAsString(decisionEvaluationInstanceKeyProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setDecisionEvaluationInstanceKey(
      final String decisionEvaluationInstanceKey) {
    decisionEvaluationInstanceKeyProp.setValue(decisionEvaluationInstanceKey);
    return this;
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionNameProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setDecisionName(final String decisionName) {
    decisionNameProp.setValue(decisionName);
    return this;
  }

  @Override
  public long getDecisionKey() {
    return decisionKeyProp.getValue();
  }

  public EvaluatedDecisionRecordV1 setDecisionKey(final long decisionKey) {
    decisionKeyProp.setValue(decisionKey);
    return this;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersionProp.getValue();
  }

  public EvaluatedDecisionRecordV1 setDecisionVersion(final int decisionVersion) {
    decisionVersionProp.setValue(decisionVersion);
    return this;
  }

  @Override
  public String getDecisionType() {
    return bufferAsString(decisionTypeProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setDecisionType(final String decisionType) {
    decisionTypeProp.setValue(decisionType);
    return this;
  }

  @Override
  public String getDecisionOutput() {
    return MsgPackConverter.convertToJson(decisionOutputProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setDecisionOutput(final DirectBuffer decisionOutput) {
    decisionOutputProp.setValue(decisionOutput);
    return this;
  }

  @Override
  public List<EvaluatedInputValue> getEvaluatedInputs() {
    final List<EvaluatedInputValue> evaluatedInputs = new ArrayList<>();

    for (final EvaluatedInputRecordV1 evaluatedInput : evaluatedInputsProp) {
      final var copyRecord = new EvaluatedInputRecordV1();
      final var copyBuffer = BufferUtil.createCopy(evaluatedInput);
      copyRecord.wrap(copyBuffer);
      evaluatedInputs.add(copyRecord);
    }

    return evaluatedInputs;
  }

  @Override
  public List<MatchedRuleValue> getMatchedRules() {
    final List<MatchedRuleValue> matchedRules = new ArrayList<>();

    for (final MatchedRuleRecordV1 matchedRule : matchedRulesProp) {
      final var copyRecord = new MatchedRuleRecordV1();
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
  public ValueArray<MatchedRuleRecordV1> matchedRules() {
    return matchedRulesProp;
  }

  @JsonIgnore
  public ValueArray<EvaluatedInputRecordV1> evaluatedInputs() {
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

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public EvaluatedDecisionRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
