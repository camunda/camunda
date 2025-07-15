/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;

public final class SignalSubscriptionRecord extends UnifiedRecordValue
    implements SignalSubscriptionRecordValue {

  // Static StringValue keys for property names
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue SIGNAL_NAME_KEY = new StringValue("signalName");
  private static final StringValue CATCH_EVENT_ID_KEY = new StringValue("catchEventId");
  private static final StringValue CATCH_EVENT_INSTANCE_KEY_KEY =
      new StringValue("catchEventInstanceKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final StringProperty signalNameProp = new StringProperty(SIGNAL_NAME_KEY, "");
  private final StringProperty catchEventIdProp = new StringProperty(CATCH_EVENT_ID_KEY, "");
  private final LongProperty catchEventInstanceKeyProp =
      new LongProperty(CATCH_EVENT_INSTANCE_KEY_KEY, -1L);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public SignalSubscriptionRecord() {
    super(6);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(signalNameProp)
        .declareProperty(catchEventIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(catchEventInstanceKeyProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final SignalSubscriptionRecord record) {
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    signalNameProp.setValue(record.getSignalNameBuffer());
    catchEventIdProp.setValue(record.getCatchEventId());
    catchEventInstanceKeyProp.setValue(record.getCatchEventInstanceKey());
    tenantIdProp.setValue(record.getTenantId());
  }

  @JsonIgnore
  public DirectBuffer getSignalNameBuffer() {
    return signalNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCatchEventIdBuffer() {
    return catchEventIdProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public String getCatchEventId() {
    return bufferAsString(catchEventIdProp.getValue());
  }

  @Override
  public long getCatchEventInstanceKey() {
    return catchEventInstanceKeyProp.getValue();
  }

  @Override
  public String getSignalName() {
    return bufferAsString(signalNameProp.getValue());
  }

  public SignalSubscriptionRecord setSignalName(final DirectBuffer signalName) {
    signalNameProp.setValue(signalName);
    return this;
  }

  public SignalSubscriptionRecord setCatchEventInstanceKey(final long catchEventInstanceKey) {
    catchEventInstanceKeyProp.setValue(catchEventInstanceKey);
    return this;
  }

  public SignalSubscriptionRecord setCatchEventId(final DirectBuffer catchEventId) {
    catchEventIdProp.setValue(catchEventId);
    return this;
  }

  public SignalSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public SignalSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public long getSubscriptionKey() {
    final long catchEventInstanceKey = catchEventInstanceKeyProp.getValue();
    return catchEventInstanceKey > -1 ? catchEventInstanceKey : processDefinitionKeyProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public SignalSubscriptionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
