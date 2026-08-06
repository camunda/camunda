/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.ConditionalStartedProcessInstanceValue;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ConditionalStartedProcessInstance extends ObjectValue
    implements ConditionalStartedProcessInstanceValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");

  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);

  public ConditionalStartedProcessInstance() {
    super(2);
    declareProperty(processDefinitionKeyProp).declareProperty(processInstanceKeyProp);
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ConditionalStartedProcessInstance setProcessDefinitionKey(
      final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public ConditionalStartedProcessInstance setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  /**
   * Copies the values from another ConditionalStartedProcessInstance.
   *
   * @param other the record to copy from
   */
  public void copy(final ConditionalStartedProcessInstance other) {
    setProcessDefinitionKey(other.getProcessDefinitionKey());
    setProcessInstanceKey(other.getProcessInstanceKey());
  }
}
