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

  public RuntimeInstructionRecord() {
    super(3);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(elementIdProperty);
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

  // This record is not used for audit log and thus process definition key has not been implemented
  @JsonIgnore
  @Override
  public long getProcessDefinitionKey() {
    return -1L;
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
