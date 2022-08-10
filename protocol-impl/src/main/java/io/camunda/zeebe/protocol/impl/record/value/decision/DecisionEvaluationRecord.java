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
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

public final class DecisionEvaluationRecord extends UnifiedRecordValue
    implements DecisionEvaluationRecordValue {

  private static final DirectBuffer NIL_DECISION_OUTPUT = BufferUtil.wrapArray(MsgPackHelper.NIL);

  private final LongProperty decisionKeyProp = new LongProperty("decisionKey");
  private final StringProperty decisionIdProp = new StringProperty("decisionId");
  private final StringProperty decisionNameProp = new StringProperty("decisionName");
  private final IntegerProperty decisionVersionProp = new IntegerProperty("decisionVersion");
  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey");
  private final BinaryProperty decisionOutputProp =
      new BinaryProperty("decisionOutput", NIL_DECISION_OUTPUT);

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final LongProperty processDefinitionKeyProp = new LongProperty("processDefinitionKey");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");

  private final ArrayProperty<EvaluatedDecisionRecord> evaluatedDecisionsProp =
      new ArrayProperty<>("evaluatedDecisions", new EvaluatedDecisionRecord());

  private final StringProperty evaluationFailureMessageProp =
      new StringProperty("evaluationFailureMessage", "");
  private final StringProperty failedDecisionIdProp = new StringProperty("failedDecisionId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public DecisionEvaluationRecord() {
    declareProperty(decisionKeyProp)
        .declareProperty(decisionIdProp)
        .declareProperty(decisionNameProp)
        .declareProperty(decisionVersionProp)
        .declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(decisionOutputProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(evaluatedDecisionsProp)
        .declareProperty(evaluationFailureMessageProp)
        .declareProperty(failedDecisionIdProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public long getDecisionKey() {
    return decisionKeyProp.getValue();
  }

  public DecisionEvaluationRecord setDecisionKey(final long decisionKey) {
    decisionKeyProp.setValue(decisionKey);
    return this;
  }

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionIdProp.getValue());
  }

  public DecisionEvaluationRecord setDecisionId(final String decisionId) {
    decisionIdProp.setValue(decisionId);
    return this;
  }

  public DecisionEvaluationRecord setDecisionId(final DirectBuffer decisionId) {
    decisionIdProp.setValue(decisionId);
    return this;
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionNameProp.getValue());
  }

  public DecisionEvaluationRecord setDecisionName(final String decisionName) {
    decisionNameProp.setValue(decisionName);
    return this;
  }

  public DecisionEvaluationRecord setDecisionName(final DirectBuffer decisionName) {
    decisionNameProp.setValue(decisionName);
    return this;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersionProp.getValue();
  }

  public DecisionEvaluationRecord setDecisionVersion(final int decisionVersion) {
    decisionVersionProp.setValue(decisionVersion);
    return this;
  }

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsIdProp.getValue());
  }

  public DecisionEvaluationRecord setDecisionRequirementsId(final String decisionRequirementsId) {
    decisionRequirementsIdProp.setValue(decisionRequirementsId);
    return this;
  }

  public DecisionEvaluationRecord setDecisionRequirementsId(
      final DirectBuffer decisionRequirementsId) {
    decisionRequirementsIdProp.setValue(decisionRequirementsId);
    return this;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
  }

  public DecisionEvaluationRecord setDecisionRequirementsKey(final long decisionRequirementsKey) {
    decisionRequirementsKeyProp.setValue(decisionRequirementsKey);
    return this;
  }

  @Override
  public String getDecisionOutput() {
    return MsgPackConverter.convertToJson(decisionOutputProp.getValue());
  }

  public DecisionEvaluationRecord setDecisionOutput(final DirectBuffer decisionOutput) {
    decisionOutputProp.setValue(decisionOutput);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  public DecisionEvaluationRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public DecisionEvaluationRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public DecisionEvaluationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public DecisionEvaluationRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  public DecisionEvaluationRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public DecisionEvaluationRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public DecisionEvaluationRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public List<EvaluatedDecisionValue> getEvaluatedDecisions() {
    final List<EvaluatedDecisionValue> evaluatedDecisions = new ArrayList<>();

    for (final EvaluatedDecisionRecord evaluatedDecision : evaluatedDecisionsProp) {
      final var copyRecord = new EvaluatedDecisionRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedDecision);
      copyRecord.wrap(copyBuffer);
      evaluatedDecisions.add(copyRecord);
    }

    return evaluatedDecisions;
  }

  @Override
  public String getEvaluationFailureMessage() {
    return bufferAsString(evaluationFailureMessageProp.getValue());
  }

  @Override
  public String getFailedDecisionId() {
    return bufferAsString(failedDecisionIdProp.getValue());
  }

  public DecisionEvaluationRecord setFailedDecisionId(final String failedDecisionId) {
    failedDecisionIdProp.setValue(failedDecisionId);
    return this;
  }

  public DecisionEvaluationRecord setEvaluationFailureMessage(
      final String evaluationFailureMessage) {
    evaluationFailureMessageProp.setValue(evaluationFailureMessage);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public DecisionEvaluationRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getEvaluationFailureMessageBuffer() {
    return evaluationFailureMessageProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getFailedDecisionIdBuffer() {
    return failedDecisionIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionOutputBuffer() {
    return decisionOutputProp.getValue();
  }

  @JsonIgnore
  public ValueArray<EvaluatedDecisionRecord> evaluatedDecisions() {
    return evaluatedDecisionsProp;
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return decisionIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return decisionNameProp.getValue();
  }
}
