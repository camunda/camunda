/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@SuppressWarnings("java:S2160")
public final class ProcessMessageSubscriptionRecord extends UnifiedRecordValue
    implements ProcessMessageSubscriptionRecordValue {

  // Static StringValue keys for property names
  private static final StringValue SUBSCRIPTION_PARTITION_ID_KEY =
      new StringValue("subscriptionPartitionId");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY = new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue MESSAGE_KEY_KEY = new StringValue("messageKey");
  private static final StringValue MESSAGE_NAME_KEY = new StringValue("messageName");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue INTERRUPTING_KEY = new StringValue("interrupting");
  private static final StringValue CORRELATION_KEY_KEY = new StringValue("correlationKey");
  private static final StringValue ELEMENT_ID_KEY = new StringValue("elementId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty(SUBSCRIPTION_PARTITION_ID_KEY);
  private final LongProperty processInstanceKeyProp = new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty elementInstanceKeyProp = new LongProperty(ELEMENT_INSTANCE_KEY_KEY);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY, -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final LongProperty messageKeyProp = new LongProperty(MESSAGE_KEY_KEY, -1L);
  private final StringProperty messageNameProp = new StringProperty(MESSAGE_NAME_KEY, "");
  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final BooleanProperty interruptingProp = new BooleanProperty(INTERRUPTING_KEY, true);
  private final StringProperty correlationKeyProp = new StringProperty(CORRELATION_KEY_KEY, "");
  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ProcessMessageSubscriptionRecord() {
    super(12);
    declareProperty(subscriptionPartitionIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(variablesProp)
        .declareProperty(interruptingProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final ProcessMessageSubscriptionRecord record) {
    setSubscriptionPartitionId(record.getSubscriptionPartitionId());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setElementInstanceKey(record.getElementInstanceKey());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageNameBuffer());
    setVariables(record.getVariablesBuffer());
    setInterrupting(record.isInterrupting());
    setBpmnProcessId(record.getBpmnProcessIdBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setElementId(record.getElementIdBuffer());
    setTenantId(record.getTenantId());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public int getSubscriptionPartitionId() {
    return subscriptionPartitionIdProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setSubscriptionPartitionId(final int partitionId) {
    subscriptionPartitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  @Override
  public String getMessageName() {
    return BufferUtil.bufferAsString(messageNameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKeyProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  @Override
  public boolean isInterrupting() {
    return interruptingProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setInterrupting(final boolean interrupting) {
    interruptingProp.setValue(interrupting);
    return this;
  }

  public ProcessMessageSubscriptionRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public ProcessMessageSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
