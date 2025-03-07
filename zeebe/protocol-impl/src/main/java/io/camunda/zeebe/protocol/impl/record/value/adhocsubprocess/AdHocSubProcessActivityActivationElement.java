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
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue.AdHocSubProcessActivityActivationElementValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class AdHocSubProcessActivityActivationElement extends ObjectValue
    implements AdHocSubProcessActivityActivationElementValue {

  private final StringProperty elementId = new StringProperty("elementId");

  public AdHocSubProcessActivityActivationElement() {
    super(1);
    declareProperty(elementId);
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId.getValue());
  }

  public AdHocSubProcessActivityActivationElement setElementId(final String elementId) {
    this.elementId.setValue(elementId);
    return this;
  }
}
