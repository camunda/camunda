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
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DecisionEvaluationRecord extends MsgpackBufferValue<DecisionEvaluationRecord>
    implements MsgpackRecordValue, DecisionEvaluationRecordValue {

  private static final DirectBuffer NIL_VALUE = BufferUtil.wrapArray(MsgPackHelper.NIL);

  long decisionKey = -1L;
  DirectBuffer decisionId = Decoding.EMPTY;
  DirectBuffer decisionName = Decoding.EMPTY;
  int decisionVersion = -1;
  DirectBuffer decisionRequirementsId = Decoding.EMPTY;
  long decisionRequirementsKey = -1L;

  @MsgpackProperty(binary = true)
  DirectBuffer decisionOutput = NIL_VALUE;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  DirectBuffer elementId = Decoding.EMPTY;
  long elementInstanceKey = -1L;
  final List<EvaluatedDecisionRecord> evaluatedDecisions = new ArrayList<>();
  DirectBuffer evaluationFailureMessage = Decoding.EMPTY;
  DirectBuffer failedDecisionId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getDecisionKey() {
    return decisionKey;
  }

  public DecisionEvaluationRecord setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
    return this;
  }

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionId);
  }

  public DecisionEvaluationRecord setDecisionId(final String decisionId) {
    this.decisionId = BufferUtil.wrapString(decisionId);
    return this;
  }

  public DecisionEvaluationRecord setDecisionId(final DirectBuffer decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionName);
  }

  public DecisionEvaluationRecord setDecisionName(final String decisionName) {
    this.decisionName = BufferUtil.wrapString(decisionName);
    return this;
  }

  public DecisionEvaluationRecord setDecisionName(final DirectBuffer decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionEvaluationRecord setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsId);
  }

  public DecisionEvaluationRecord setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = BufferUtil.wrapString(decisionRequirementsId);
    return this;
  }

  public DecisionEvaluationRecord setDecisionRequirementsId(
      final DirectBuffer decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionEvaluationRecord setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  @Override
  public String getDecisionOutput() {
    return MsgPackConverter.convertToJson(decisionOutput);
  }

  public DecisionEvaluationRecord setDecisionOutput(final DirectBuffer decisionOutput) {
    this.decisionOutput = decisionOutput;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public DecisionEvaluationRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public DecisionEvaluationRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionEvaluationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionEvaluationRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  public DecisionEvaluationRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public DecisionEvaluationRecord setElementId(final DirectBuffer elementId) {
    this.elementId = elementId;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public DecisionEvaluationRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  @Override
  public List<EvaluatedDecisionValue> getEvaluatedDecisions() {
    final List<EvaluatedDecisionValue> copies = new ArrayList<>();
    for (final EvaluatedDecisionRecord evaluatedDecision : evaluatedDecisions) {
      final var copyRecord = new EvaluatedDecisionRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedDecision);
      copyRecord.wrap(copyBuffer);
      copies.add(copyRecord);
    }
    return copies;
  }

  @Override
  public String getEvaluationFailureMessage() {
    return bufferAsString(evaluationFailureMessage);
  }

  @Override
  public String getFailedDecisionId() {
    return bufferAsString(failedDecisionId);
  }

  public DecisionEvaluationRecord setFailedDecisionId(final String failedDecisionId) {
    this.failedDecisionId = BufferUtil.wrapString(failedDecisionId);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public DecisionEvaluationRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public DecisionEvaluationRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public DecisionEvaluationRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  public DecisionEvaluationRecord setEvaluationFailureMessage(
      final String evaluationFailureMessage) {
    this.evaluationFailureMessage = BufferUtil.wrapString(evaluationFailureMessage);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public DecisionEvaluationRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getEvaluationFailureMessageBuffer() {
    return evaluationFailureMessage;
  }

  @JsonIgnore
  public DirectBuffer getFailedDecisionIdBuffer() {
    return failedDecisionId;
  }

  @JsonIgnore
  public DirectBuffer getDecisionOutputBuffer() {
    return decisionOutput;
  }

  @JsonIgnore
  public ValueArray<EvaluatedDecisionRecord> evaluatedDecisions() {
    return new EvaluatedDecisions(evaluatedDecisions);
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsId;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return decisionId;
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return decisionName;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public DecisionEvaluationRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public DecisionEvaluationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<DecisionEvaluationRecord> codec() {
    return DecisionEvaluationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DecisionEvaluationRecord> ops() {
    return DecisionEvaluationRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code evaluatedDecisions}: it adds and removes the elements of the
   * list, so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class EvaluatedDecisions implements ValueArray<EvaluatedDecisionRecord> {

    final List<EvaluatedDecisionRecord> decisions;

    private EvaluatedDecisions(final List<EvaluatedDecisionRecord> decisions) {
      this.decisions = decisions;
    }

    @Override
    public EvaluatedDecisionRecord add() {
      final var decision = new EvaluatedDecisionRecord();
      decisions.add(decision);
      return decision;
    }

    @Override
    public EvaluatedDecisionRecord add(final int index) {
      final var decision = new EvaluatedDecisionRecord();
      decisions.add(index, decision);
      return decision;
    }

    @Override
    public EvaluatedDecisionRecord get(final int index) {
      return decisions.get(index);
    }

    @Override
    public EvaluatedDecisionRecord remove(final int index) {
      return decisions.remove(index);
    }

    @Override
    public int size() {
      return decisions.size();
    }

    @Override
    public Stream<EvaluatedDecisionRecord> stream() {
      return decisions.stream();
    }

    @Override
    public Iterator<EvaluatedDecisionRecord> iterator() {
      return decisions.iterator();
    }
  }
}
