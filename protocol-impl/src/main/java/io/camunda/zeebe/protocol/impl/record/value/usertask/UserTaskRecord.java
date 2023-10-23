/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.msgpack.value.StringValue.EMPTY_STRING;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_BPMN_PROCESS_ID;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_INSTANCE_KEY;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import org.agrona.DirectBuffer;

public class UserTaskRecord extends UnifiedRecordValue implements UserTaskRecordValue {

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROP_PROCESS_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_PROCESS_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public UserTaskRecord() {
    declareProperty(processInstanceKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final UserTaskRecord record) {
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessId());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processDefinitionVersionProp.setValue(record.getProcessDefinitionVersion());
    elementIdProp.setValue(record.getElementId());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
    tenantIdProp.setValue(record.getTenantId());
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public UserTaskRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public UserTaskRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public UserTaskRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  public UserTaskRecord setProcessDefinitionVersion(final int processDefinitionVersion) {
    processDefinitionVersionProp.setValue(processDefinitionVersion);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public UserTaskRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public UserTaskRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }
}
