/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
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

  private final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructionsProperty =
      new ArrayProperty<>("startInstructions", new ProcessInstanceCreationStartInstruction());
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public ProcessInstanceCreationRecord() {
    declareProperty(bpmnProcessIdProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty)
        .declareProperty(fetchVariablesProperty)
        .declareProperty(startInstructionsProperty)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  @Override
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

  @Override
  public List<ProcessInstanceCreationStartInstructionValue> getStartInstructions() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return startInstructionsProperty.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceCreationStartInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceCreationStartInstructionValue) elementCopy;
            })
        .toList();
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
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessInstanceCreationRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  @JsonIgnore
  public boolean hasStartInstructions() {
    return !startInstructionsProperty.isEmpty();
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

  public ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions() {
    return startInstructionsProperty;
  }

  public ProcessInstanceCreationRecord addStartInstructions(
      final List<ProcessInstanceCreationStartInstruction> startInstructions) {
    startInstructions.forEach(this::addStartInstruction);
    return this;
  }

  public ProcessInstanceCreationRecord addStartInstruction(
      final ProcessInstanceCreationStartInstruction startInstruction) {
    startInstructionsProperty.add().copy(startInstruction);
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
