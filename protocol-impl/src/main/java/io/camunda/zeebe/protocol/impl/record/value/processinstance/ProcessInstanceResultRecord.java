/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class ProcessInstanceResultRecord extends UnifiedRecordValue
    implements ProcessInstanceResultRecordValue {

  private final StringProperty bpmnProcessIdProperty = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1);
  private final IntegerProperty versionProperty = new IntegerProperty("version", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);

  public ProcessInstanceResultRecord() {
    declareProperty(bpmnProcessIdProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProperty.getValue();
  }

  public ProcessInstanceResultRecord setVersion(final int version) {
    versionProperty.setValue(version);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceResultRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProperty.setValue(key);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceResultRecord setProcessInstanceKey(final long instanceKey) {
    processInstanceKeyProperty.setValue(instanceKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public ProcessInstanceResultRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }
}
