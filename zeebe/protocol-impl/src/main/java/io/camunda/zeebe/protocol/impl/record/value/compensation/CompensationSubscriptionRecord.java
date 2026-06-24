/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.compensation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class CompensationSubscriptionRecord extends UnifiedRecordValue
    implements CompensationSubscriptionRecordValue {

  private static final String EMPTY_STRING = "";

  private final StringProperty tenantIdProperty =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1);
  private final StringProperty compensableActivityIdProperty =
      new StringProperty("compensableActivityId", EMPTY_STRING);
  private final StringProperty throwEventIdProperty =
      new StringProperty("throwEventId", EMPTY_STRING);
  private final LongProperty throwEventInstanceKeyProperty =
      new LongProperty("throwEventInstanceKey", -1);
  private final StringProperty compensationHandlerIdProperty =
      new StringProperty("compensationHandlerId", EMPTY_STRING);
  private final LongProperty compensationHandlerInstanceKeyProperty =
      new LongProperty("compensationHandlerInstanceKey", -1L);

  private final LongProperty compensableActivityScopeKeyProperty =
      new LongProperty("compensableActivityScopeKey", -1);

  private final LongProperty compensableActivityInstanceKeyProperty =
      new LongProperty("compensableActivityInstanceKey", -1);

  private final DocumentProperty variablesProperty = new DocumentProperty("variables");

  public CompensationSubscriptionRecord() {
    super(11);
    declareProperty(tenantIdProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(compensableActivityIdProperty)
        .declareProperty(throwEventIdProperty)
        .declareProperty(throwEventInstanceKeyProperty)
        .declareProperty(compensationHandlerIdProperty)
        .declareProperty(compensationHandlerInstanceKeyProperty)
        .declareProperty(compensableActivityScopeKeyProperty)
        .declareProperty(compensableActivityInstanceKeyProperty)
        .declareProperty(variablesProperty);
  }

  public void wrap(final CompensationSubscriptionRecord record) {
    tenantIdProperty.setValue(record.getTenantId());
    processInstanceKeyProperty.setValue(record.getProcessInstanceKey());
    processDefinitionKeyProperty.setValue(record.getProcessDefinitionKey());
    compensableActivityIdProperty.setValue(record.getCompensableActivityId());
    throwEventIdProperty.setValue(record.getThrowEventId());
    throwEventInstanceKeyProperty.setValue(record.getThrowEventInstanceKey());
    compensationHandlerIdProperty.setValue(record.getCompensationHandlerId());
    compensationHandlerInstanceKeyProperty.setValue(record.getCompensationHandlerInstanceKey());
    compensableActivityScopeKeyProperty.setValue(record.getCompensableActivityScopeKey());
    compensableActivityInstanceKeyProperty.setValue(record.getCompensableActivityInstanceKey());
    variablesProperty.setValue(record.getVariablesBuffer());
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProperty.getValue());
  }

  public CompensationSubscriptionRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public CompensationSubscriptionRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public CompensationSubscriptionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getCompensableActivityId() {
    return BufferUtil.bufferAsString(compensableActivityIdProperty.getValue());
  }

  @Override
  public String getThrowEventId() {
    return BufferUtil.bufferAsString(throwEventIdProperty.getValue());
  }

  @Override
  public long getThrowEventInstanceKey() {
    return throwEventInstanceKeyProperty.getValue();
  }

  @Override
  public String getCompensationHandlerId() {
    return BufferUtil.bufferAsString(compensationHandlerIdProperty.getValue());
  }

  @Override
  public long getCompensationHandlerInstanceKey() {
    return compensationHandlerInstanceKeyProperty.getValue();
  }

  @Override
  public long getCompensableActivityScopeKey() {
    return compensableActivityScopeKeyProperty.getValue();
  }

  @Override
  public long getCompensableActivityInstanceKey() {
    return compensableActivityInstanceKeyProperty.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public CompensationSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityInstanceKey(
      final long compensableActivityInstanceKey) {
    compensableActivityInstanceKeyProperty.setValue(compensableActivityInstanceKey);
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityScopeKey(
      final long scopeInstanceKey) {
    compensableActivityScopeKeyProperty.setValue(scopeInstanceKey);
    return this;
  }

  public CompensationSubscriptionRecord setCompensationHandlerInstanceKey(
      final long compensationHandlerInstanceKey) {
    compensationHandlerInstanceKeyProperty.setValue(compensationHandlerInstanceKey);
    return this;
  }

  public CompensationSubscriptionRecord setCompensationHandlerId(
      final String compensationHandlerId) {
    compensationHandlerIdProperty.setValue(compensationHandlerId);
    return this;
  }

  public CompensationSubscriptionRecord setThrowEventInstanceKey(final long throwEventInstanceKey) {
    throwEventInstanceKeyProperty.setValue(throwEventInstanceKey);
    return this;
  }

  public CompensationSubscriptionRecord setThrowEventId(final String throwEventId) {
    throwEventIdProperty.setValue(throwEventId);
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityId(
      final String compensableActivityId) {
    compensableActivityIdProperty.setValue(compensableActivityId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }
}
