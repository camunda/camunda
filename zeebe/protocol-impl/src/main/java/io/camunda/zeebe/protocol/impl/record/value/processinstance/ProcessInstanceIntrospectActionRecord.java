/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceIntrospectRecordValue.ProcessInstanceIntrospectActionRecordValue;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public class ProcessInstanceIntrospectActionRecord extends ObjectValue
    implements ProcessInstanceIntrospectActionRecordValue {

  private final StringProperty actionProperty = new StringProperty("action");
  private final LongProperty elementInstanceKey = new LongProperty("elementInstanceKey");
  private final DocumentProperty parametersProperty = new DocumentProperty("parameters");

  public ProcessInstanceIntrospectActionRecord() {
    super(3);
    declareProperty(actionProperty)
        .declareProperty(elementInstanceKey)
        .declareProperty(parametersProperty);
  }

  public void copy(final ProcessInstanceIntrospectActionRecordValue object) {
    actionProperty.setValue(object.getAction());
    elementInstanceKey.setValue(object.getElementInstanceKey());
    setParameters(object.getParameters());
  }

  @Override
  public String getAction() {
    return bufferAsString(actionProperty.getValue());
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey.getValue();
  }

  public ProcessInstanceIntrospectActionRecord setElementInstanceKey(final Long key) {
    elementInstanceKey.setValue(key);
    return this;
  }

  @Override
  public Map<String, String> getParameters() {
    return MsgPackConverter.convertToStringMap(parametersProperty.getValue());
  }

  public ProcessInstanceIntrospectActionRecord setParameters(final Map<String, String> parameters) {
    parametersProperty.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(parameters)));
    return this;
  }

  public ProcessInstanceIntrospectActionRecord setAction(final String action) {
    actionProperty.setValue(action);
    return this;
  }
}
