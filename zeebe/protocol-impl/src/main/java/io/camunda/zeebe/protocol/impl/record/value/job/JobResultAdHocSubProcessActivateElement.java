/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultAdHocSubProcessActivateElementValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public class JobResultAdHocSubProcessActivateElement extends UnpackedObject
    implements JobResultAdHocSubProcessActivateElementValue {

  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");

  public JobResultAdHocSubProcessActivateElement() {
    super(2);
    declareProperty(elementIdProp).declareProperty(variablesProp);
  }

  public void wrap(final JobResultAdHocSubProcessActivateElement other) {
    setElementId(other.getElementId());
    setVariables(other.getVariablesBuffer());
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public JobResultAdHocSubProcessActivateElement setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  public JobResultAdHocSubProcessActivateElement setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }
}
