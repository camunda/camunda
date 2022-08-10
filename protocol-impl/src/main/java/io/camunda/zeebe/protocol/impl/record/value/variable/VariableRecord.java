/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class VariableRecord extends UnifiedRecordValue implements VariableRecordValue {

  private final StringProperty nameProp = new StringProperty("name");
  private final BinaryProperty valueProp = new BinaryProperty("value");
  private final LongProperty scopeKeyProp = new LongProperty("scopeKey");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final LongProperty processDefinitionKeyProp = new LongProperty("processDefinitionKey");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public VariableRecord() {
    declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public String getValue() {
    return MsgPackConverter.convertToJson(valueProp.getValue());
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public VariableRecord setScopeKey(final long scopeKey) {
    scopeKeyProp.setValue(scopeKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public VariableRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public VariableRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public VariableRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  public VariableRecord setValue(final DirectBuffer value) {
    valueProp.setValue(value);
    return this;
  }

  public VariableRecord setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public VariableRecord setValue(final DirectBuffer value, final int offset, final int length) {
    valueProp.setValue(value, offset, length);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return valueProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }
}
