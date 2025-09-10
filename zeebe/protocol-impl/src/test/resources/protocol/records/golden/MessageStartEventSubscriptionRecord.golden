/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class MessageStartEventSubscriptionRecord extends UnifiedRecordValue
    implements MessageStartEventSubscriptionRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue MESSAGE_NAME_KEY = new StringValue("messageName");
  private static final StringValue START_EVENT_ID_KEY = new StringValue("startEventId");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue MESSAGE_KEY_KEY = new StringValue("messageKey");
  private static final StringValue CORRELATION_KEY_KEY = new StringValue("correlationKey");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final StringProperty messageNameProp = new StringProperty(MESSAGE_NAME_KEY, "");
  private final StringProperty startEventIdProp = new StringProperty(START_EVENT_ID_KEY, "");

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);
  private final LongProperty messageKeyProp = new LongProperty(MESSAGE_KEY_KEY, -1L);
  private final StringProperty correlationKeyProp = new StringProperty(CORRELATION_KEY_KEY, "");
  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public MessageStartEventSubscriptionRecord() {
    super(9);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(startEventIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(variablesProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final MessageStartEventSubscriptionRecord record) {
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    messageNameProp.setValue(record.getMessageNameBuffer());
    startEventIdProp.setValue(record.getStartEventIdBuffer());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    messageKeyProp.setValue(record.getMessageKey());
    correlationKeyProp.setValue(record.getCorrelationKeyBuffer());
    variablesProp.setValue(record.getVariablesBuffer());
    tenantIdProp.setValue(record.getTenantId());
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventIdProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public String getStartEventId() {
    return bufferAsString(startEventIdProp.getValue());
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageNameProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public MessageStartEventSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  public MessageStartEventSubscriptionRecord setStartEventId(final DirectBuffer startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  public MessageStartEventSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
