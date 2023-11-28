/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.compensation;

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
  private final StringProperty elementActivityIdProperty =
      new StringProperty("elementActivityId", EMPTY_STRING);
  private final LongProperty flowScopeElementActivityIdProperty =
      new LongProperty("flowScopeElementActivityId", -1);
  private final StringProperty elementThrowEventIdProperty =
      new StringProperty("elementThrowEventId", EMPTY_STRING);
  private final LongProperty elementThrowEventKeyProperty =
      new LongProperty("elementThrowEventKey", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");

  public CompensationSubscriptionRecord() {
    declareProperty(tenantIdProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(elementActivityIdProperty)
        .declareProperty(flowScopeElementActivityIdProperty)
        .declareProperty(elementThrowEventIdProperty)
        .declareProperty(elementThrowEventKeyProperty)
        .declareProperty(variablesProperty);
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
  public String getElementActivityId() {
    return BufferUtil.bufferAsString(elementActivityIdProperty.getValue());
  }

  public CompensationSubscriptionRecord setElementActivityId(final String elementActivityId) {
    elementActivityIdProperty.setValue(elementActivityId);
    return this;
  }

  @Override
  public long getFlowScopeElementActivityId() {
    return flowScopeElementActivityIdProperty.getValue();
  }

  public CompensationSubscriptionRecord setFlowScopeElementActivityId(
      final long flowScopeElementActivityId) {
    flowScopeElementActivityIdProperty.setValue(flowScopeElementActivityId);
    return this;
  }

  @Override
  public String getElementThrowEventId() {
    return BufferUtil.bufferAsString(elementThrowEventIdProperty.getValue());
  }

  public CompensationSubscriptionRecord setElementThrowEventId(final String elementThrowEventId) {
    elementThrowEventIdProperty.setValue(elementThrowEventId);
    return this;
  }

  @Override
  public long getElementThrowEventKey() {
    return elementThrowEventKeyProperty.getValue();
  }

  public CompensationSubscriptionRecord setElementThrowEventKey(final long elementThrowEventKey) {
    elementThrowEventKeyProperty.setValue(elementThrowEventKey);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public CompensationSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }
}
