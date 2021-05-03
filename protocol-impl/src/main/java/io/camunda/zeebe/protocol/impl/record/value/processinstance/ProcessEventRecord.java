/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@SuppressWarnings("java:S2160")
public final class ProcessEventRecord extends UnifiedRecordValue
    implements ProcessEventRecordValue {
  private final LongProperty scopeKeyProperty = new LongProperty("scopeKey");
  private final StringProperty targetElementIdProperty = new StringProperty("targetElementId");
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1);
  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);

  public ProcessEventRecord() {
    declareProperty(scopeKeyProperty)
        .declareProperty(targetElementIdProperty)
        .declareProperty(variablesProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty);
  }

  public ProcessEventRecord wrap(final ProcessEventRecord record) {
    scopeKeyProperty.setValue(record.getScopeKey());
    targetElementIdProperty.setValue(record.getTargetElementIdBuffer());
    variablesProperty.setValue(record.getVariablesBuffer());
    processDefinitionKeyProperty.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProperty.setValue(record.getProcessInstanceKey());

    return this;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementIdProperty.getValue();
  }

  public ProcessEventRecord setTargetElementIdBuffer(final DirectBuffer targetElementIdBuffer) {
    targetElementIdProperty.setValue(targetElementIdBuffer);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  public ProcessEventRecord setVariablesBuffer(final DirectBuffer variablesBuffer) {
    variablesProperty.setValue(variablesBuffer);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(getVariablesBuffer());
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public ProcessEventRecord setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(getTargetElementIdBuffer());
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessEventRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessEventRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }
}
