/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class VariableRecord extends UnifiedRecordValue implements VariableRecordValue {

  // Static StringValue keys for property names
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue VALUE_KEY = new StringValue("value");
  private static final StringValue SCOPE_KEY_KEY = new StringValue("scopeKey");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue ROOT_PROCESS_INSTANCE_KEY_KEY =
      new StringValue("rootProcessInstanceKey");

  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final BinaryProperty valueProp = new BinaryProperty(VALUE_KEY);
  private final LongProperty scopeKeyProp = new LongProperty(SCOPE_KEY_KEY);
  private final LongProperty processInstanceKeyProp = new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY);
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty rootProcessInstanceKeyProp =
      new LongProperty(ROOT_PROCESS_INSTANCE_KEY_KEY, -1L);

  public VariableRecord() {
    super(8);
    declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(rootProcessInstanceKeyProp);
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

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProp.getValue();
  }

  public VariableRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    rootProcessInstanceKeyProp.setValue(rootProcessInstanceKey);
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

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public VariableRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
