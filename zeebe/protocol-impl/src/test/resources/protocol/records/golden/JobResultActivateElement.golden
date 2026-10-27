/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public final class JobResultActivateElement extends UnpackedObject
    implements JobResultActivateElementValue {

  private static final StringValue ELEMENT_ID_KEY = new StringValue("elementId");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");

  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY);
  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);

  public JobResultActivateElement() {
    super(2);
    declareProperty(elementIdProp).declareProperty(variablesProp);
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  public JobResultActivateElement setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public JobResultActivateElement setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }
}
