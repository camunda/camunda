/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.condition;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ConditionSubscriptionRecord extends UnifiedRecordValue
    implements ConditionSubscriptionRecordValue {

  private static final StringValue SCOPE_KEY_KEY = new StringValue("scopeKey");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue CATCH_EVENT_ID_KEY = new StringValue("catchEventId");
  private static final StringValue INTERRUPTING_KEY = new StringValue("interrupting");
  private static final StringValue CONDITION_KEY = new StringValue("condition");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  // default to -1 for root level start events
  private final LongProperty scopeKeyProp = new LongProperty(SCOPE_KEY_KEY, -1L);
  private final LongProperty processInstanceKeyProp = new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty elementInstanceKeyProp = new LongProperty(ELEMENT_INSTANCE_KEY_KEY);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY);
  private final StringProperty catchEventIdProp = new StringProperty(CATCH_EVENT_ID_KEY, "");
  private final BooleanProperty interruptingProp = new BooleanProperty(INTERRUPTING_KEY, true);
  private final StringProperty conditionProp = new StringProperty(CONDITION_KEY);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ConditionSubscriptionRecord() {
    super(8);
    declareProperty(scopeKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(catchEventIdProp)
        .declareProperty(interruptingProp)
        .declareProperty(conditionProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final ConditionSubscriptionRecord record) {
    scopeKeyProp.setValue(record.getScopeKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    catchEventIdProp.setValue(record.getCatchEventId());
    interruptingProp.setValue(record.isInterrupting());
    conditionProp.setValue(record.getCondition());
    tenantIdProp.setValue(record.getTenantId());
  }

  /**
   * The key of the scope in which the condition is evaluated. Scopes should be assigned for
   * different element types as follows:
   *
   * <p>Intermediate catch event → element itself
   *
   * <p>Boundary event → attached activity
   *
   * <p>Event subprocess start event → flow scope that is enclosing the event subprocess
   *
   * <p>Root level start event → nothing, just evaluate through endpoint call using process
   * definition key
   *
   * @return the scope key
   */
  @Override
  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public ConditionSubscriptionRecord setScopeKey(final long key) {
    scopeKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public ConditionSubscriptionRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  /**
   * The process definition key used for root level start events to evaluate the condition.
   *
   * @return the process definition key
   */
  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ConditionSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getCatchEventId() {
    return bufferAsString(catchEventIdProp.getValue());
  }

  public ConditionSubscriptionRecord setCatchEventId(final DirectBuffer catchEventId) {
    catchEventIdProp.setValue(catchEventId);
    return this;
  }

  @Override
  public String getCondition() {
    return BufferUtil.bufferAsString(conditionProp.getValue());
  }

  public ConditionSubscriptionRecord setCondition(final DirectBuffer condition) {
    conditionProp.setValue(condition);
    return this;
  }

  @Override
  public boolean isInterrupting() {
    return interruptingProp.getValue();
  }

  public ConditionSubscriptionRecord setInterrupting(final boolean interrupting) {
    interruptingProp.setValue(interrupting);
    return this;
  }

  public ConditionSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  public DirectBuffer getCatchEventIdBuffer() {
    return catchEventIdProp.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ConditionSubscriptionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
