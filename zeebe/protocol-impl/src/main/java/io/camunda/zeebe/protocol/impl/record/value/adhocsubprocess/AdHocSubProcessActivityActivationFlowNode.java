/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue.AdHocSubProcessActivityActivationFlowNodeValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class AdHocSubProcessActivityActivationFlowNode extends ObjectValue
    implements AdHocSubProcessActivityActivationFlowNodeValue {

  private final StringProperty flowNodeId = new StringProperty("flowNodeId");

  public AdHocSubProcessActivityActivationFlowNode() {
    super(1);
    declareProperty(flowNodeId);
  }

  @Override
  public String getFlowNodeId() {
    return BufferUtil.bufferAsString(flowNodeId.getValue());
  }

  public AdHocSubProcessActivityActivationFlowNode setFlowNodeId(final String flowNodeId) {
    this.flowNodeId.setValue(flowNodeId);
    return this;
  }
}
