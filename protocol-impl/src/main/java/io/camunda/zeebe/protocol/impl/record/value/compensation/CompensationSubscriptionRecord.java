/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
  private final LongProperty compensableActivityScopeIdProperty =
      new LongProperty("compensableActivityScopeId", -1);
  private final StringProperty throwEventIdProperty =
      new StringProperty("throwEventId", EMPTY_STRING);
  private final LongProperty throwEventInstanceKeyProperty =
      new LongProperty("throwEventInstanceKey", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");

  public CompensationSubscriptionRecord() {
    super(8);
    declareProperty(tenantIdProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(compensableActivityIdProperty)
        .declareProperty(compensableActivityScopeIdProperty)
        .declareProperty(throwEventIdProperty)
        .declareProperty(throwEventInstanceKeyProperty)
        .declareProperty(variablesProperty);
  }

  public void wrap(final CompensationSubscriptionRecord record) {
    tenantIdProperty.setValue(record.getTenantId());
    processInstanceKeyProperty.setValue(record.getProcessInstanceKey());
    processDefinitionKeyProperty.setValue(record.getProcessDefinitionKey());
    compensableActivityIdProperty.setValue(record.getCompensableActivityId());
    compensableActivityScopeIdProperty.setValue(record.getCompensableActivityScopeId());
    throwEventIdProperty.setValue(record.getThrowEventId());
    throwEventInstanceKeyProperty.setValue(record.getThrowEventInstanceKey());
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
  public long getCompensableActivityScopeId() {
    return compensableActivityScopeIdProperty.getValue();
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
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public CompensationSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
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

  public CompensationSubscriptionRecord setCompensableActivityScopeId(
      final long compensableActivityScopeId) {
    compensableActivityScopeIdProperty.setValue(compensableActivityScopeId);
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
