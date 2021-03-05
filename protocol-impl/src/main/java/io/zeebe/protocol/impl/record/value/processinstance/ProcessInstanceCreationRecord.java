/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.processinstance;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class ProcessInstanceCreationRecord extends UnifiedRecordValue
    implements ProcessInstanceCreationRecordValue {

  private final StringProperty bpmnProcessIdProperty = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1);
  private final IntegerProperty versionProperty = new IntegerProperty("version", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);
  private final ArrayProperty<StringValue> fetchVariablesProperty =
      new ArrayProperty<>("fetchVariables", new StringValue());

  public ProcessInstanceCreationRecord() {
    declareProperty(bpmnProcessIdProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty)
        .declareProperty(fetchVariablesProperty);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public int getVersion() {
    return versionProperty.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceCreationRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProperty.setValue(key);
    return this;
  }

  public ProcessInstanceCreationRecord setVersion(final int version) {
    versionProperty.setValue(version);
    return this;
  }

  public ProcessInstanceCreationRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public ProcessInstanceCreationRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceCreationRecord setProcessInstanceKey(final long instanceKey) {
    processInstanceKeyProperty.setValue(instanceKey);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public ProcessInstanceCreationRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  public ArrayProperty<StringValue> fetchVariables() {
    return fetchVariablesProperty;
  }

  public ProcessInstanceCreationRecord setFetchVariables(final List<String> fetchVariables) {
    fetchVariables.forEach(variable -> fetchVariablesProperty.add().wrap(wrapString(variable)));
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
}
