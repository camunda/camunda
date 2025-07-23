/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionInterruptionRecordValue;
import org.agrona.DirectBuffer;

public class RuntimeInstructionInterruptionRecord extends UnifiedRecordValue
    implements RuntimeInstructionInterruptionRecordValue {

  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue INTERRUPTING_ELEMENT_ID_KEY =
      new StringValue("interruptingElementId");

  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1);
  private final StringProperty tenantIdProperty = new StringProperty(TENANT_ID_KEY, "");
  private final StringProperty interruptingElementIdProperty =
      new StringProperty(INTERRUPTING_ELEMENT_ID_KEY, "");

  public RuntimeInstructionInterruptionRecord() {
    super(3);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(interruptingElementIdProperty);
  }

  @Override
  public String getInterruptingElementId() {
    return bufferAsString(interruptingElementIdProperty.getValue());
  }

  public void setInterruptingElementId(final String interruptingElementId) {
    interruptingElementIdProperty.setValue(interruptingElementId);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public void setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
  }

  public DirectBuffer getInterruptingElementIdBuffer() {
    return interruptingElementIdProperty.getValue();
  }
}
