/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.consent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import io.camunda.zeebe.protocol.record.value.ConsentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

public final class ConsentRecord extends UnifiedRecordValue implements ConsentRecordValue {

  private final LongProperty consentKeyProp = new LongProperty("consentKey", -1L);
  private final StringProperty subjectProp = new StringProperty("subject", "");
  private final StringProperty requesterProp = new StringProperty("requester", "");
  private final StringProperty connectionProp = new StringProperty("connection", "");
  private final StringProperty operationProp = new StringProperty("operation", "");
  private final EnumProperty<ConsentDecision> decisionProp =
      new EnumProperty<>("decision", ConsentDecision.class, ConsentDecision.UNSPECIFIED);
  private final DocumentProperty changeProp = new DocumentProperty("change");
  private final StringProperty reasonProp = new StringProperty("reason", "");
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>("jobKeys", LongValue::new);

  public ConsentRecord() {
    super(15);
    declareProperty(consentKeyProp)
        .declareProperty(subjectProp)
        .declareProperty(requesterProp)
        .declareProperty(connectionProp)
        .declareProperty(operationProp)
        .declareProperty(decisionProp)
        .declareProperty(changeProp)
        .declareProperty(reasonProp)
        .declareProperty(jobKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(elementIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(jobKeysProp);
  }

  @Override
  public long getConsentKey() {
    return consentKeyProp.getValue();
  }

  public ConsentRecord setConsentKey(final long consentKey) {
    consentKeyProp.setValue(consentKey);
    return this;
  }

  @Override
  public String getSubject() {
    return BufferUtil.bufferAsString(subjectProp.getValue());
  }

  public ConsentRecord setSubject(final String subject) {
    subjectProp.setValue(subject);
    return this;
  }

  @Override
  public String getRequester() {
    return BufferUtil.bufferAsString(requesterProp.getValue());
  }

  public ConsentRecord setRequester(final String requester) {
    requesterProp.setValue(requester);
    return this;
  }

  @Override
  public String getConnection() {
    return BufferUtil.bufferAsString(connectionProp.getValue());
  }

  public ConsentRecord setConnection(final String connection) {
    connectionProp.setValue(connection);
    return this;
  }

  @Override
  public String getOperation() {
    return BufferUtil.bufferAsString(operationProp.getValue());
  }

  public ConsentRecord setOperation(final String operation) {
    operationProp.setValue(operation);
    return this;
  }

  @Override
  public ConsentDecision getDecision() {
    return decisionProp.getValue();
  }

  public ConsentRecord setDecision(final ConsentDecision decision) {
    decisionProp.setValue(decision);
    return this;
  }

  @Override
  public Map<String, Object> getChange() {
    return MsgPackConverter.convertToMap(changeProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getChangeBuffer() {
    return changeProp.getValue();
  }

  public ConsentRecord setChange(final DirectBuffer change) {
    changeProp.setValue(change);
    return this;
  }

  @Override
  public String getReason() {
    return BufferUtil.bufferAsString(reasonProp.getValue());
  }

  public ConsentRecord setReason(final String reason) {
    reasonProp.setValue(reason);
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public ConsentRecord setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public ConsentRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public ConsentRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public ConsentRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public ConsentRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public List<Long> getJobKeys() {
    return StreamSupport.stream(jobKeysProp.spliterator(), false).map(LongValue::getValue).toList();
  }

  public ConsentRecord addJobKey(final long jobKey) {
    jobKeysProp.add().setValue(jobKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ConsentRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
