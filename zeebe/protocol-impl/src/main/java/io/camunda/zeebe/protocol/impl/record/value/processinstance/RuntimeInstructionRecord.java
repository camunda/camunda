/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionRecordValue;
import org.agrona.DirectBuffer;

public class RuntimeInstructionRecord extends UnifiedRecordValue
    implements RuntimeInstructionRecordValue {

  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);
  private final StringProperty tenantIdProperty = new StringProperty("tenantId", "");
  private final StringProperty elementIdProperty = new StringProperty("elementId", "");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1L);

  public RuntimeInstructionRecord() {
    super(4);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(elementIdProperty)
        .declareProperty(processDefinitionKeyProperty);
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProperty.getValue());
  }

  public RuntimeInstructionRecord setElementId(final String interruptingElementId) {
    elementIdProperty.setValue(interruptingElementId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public RuntimeInstructionRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public RuntimeInstructionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public RuntimeInstructionRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProperty.getValue();
  }
}
