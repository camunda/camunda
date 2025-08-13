/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class ProcessInstanceCreationRecord extends UnifiedRecordValue
    implements ProcessInstanceCreationRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue VERSION_KEY = new StringValue("version");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue FETCH_VARIABLES_KEY = new StringValue("fetchVariables");
  private static final StringValue START_INSTRUCTIONS_KEY = new StringValue("startInstructions");
  private static final StringValue RUNTIME_INSTRUCTIONS_KEY =
      new StringValue("runtimeInstructions");
  private static final StringValue TAGS_KEY = new StringValue("tags");

  private final StringProperty bpmnProcessIdProperty = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1);
  private final IntegerProperty versionProperty = new IntegerProperty(VERSION_KEY, -1);
  private final StringProperty tenantIdProperty =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final DocumentProperty variablesProperty = new DocumentProperty(VARIABLES_KEY);
  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1);
  private final ArrayProperty<StringValue> fetchVariablesProperty =
      new ArrayProperty<>(FETCH_VARIABLES_KEY, StringValue::new);
  private final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructionsProperty =
      new ArrayProperty<>(START_INSTRUCTIONS_KEY, ProcessInstanceCreationStartInstruction::new);

  private final ArrayProperty<ProcessInstanceCreationRuntimeInstruction>
      runtimeInstructionsProperty =
          new ArrayProperty<>(
              RUNTIME_INSTRUCTIONS_KEY, ProcessInstanceCreationRuntimeInstruction::new);
  private final ArrayProperty<StringValue> tagsProperty =
      new ArrayProperty<>(TAGS_KEY, StringValue::new);

  public ProcessInstanceCreationRecord() {
    super(10);
    declareProperty(bpmnProcessIdProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty)
        .declareProperty(fetchVariablesProperty)
        .declareProperty(startInstructionsProperty)
        .declareProperty(runtimeInstructionsProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(tagsProperty);
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

  @Override
  public List<ProcessInstanceCreationRuntimeInstructionValue> getRuntimeInstructions() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return runtimeInstructionsProperty.stream()
        .map(
            element -> {
              final ProcessInstanceCreationRuntimeInstruction elementCopy =
                  ProcessInstanceCreationRuntimeInstruction.createInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceCreationRuntimeInstructionValue) elementCopy;
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

  public ProcessInstanceCreationRecord addRuntimeInstructions(
      final List<ProcessInstanceCreationRuntimeInstruction> runtimeInstructions) {
    if (runtimeInstructions == null) {
      runtimeInstructionsProperty.reset();
      return this;
    }
    runtimeInstructions.forEach(this::addRuntimeInstruction);
    return this;
  }

  public ProcessInstanceCreationRecord addRuntimeInstruction(
      final ProcessInstanceCreationRuntimeInstruction runtimeInstruction) {
    runtimeInstructionsProperty.add().copy(runtimeInstruction);
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
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public ProcessInstanceCreationRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @Override
  public Set<String> getTags() {
    final var tags = new HashSet<String>();
    tagsProperty.forEach(e -> tags.add(e.toString()));
    return tags;
  }

  public ProcessInstanceCreationRecord setTags(final Set<String> tags) {
    tagsProperty.reset();
    tags.forEach(e -> tagsProperty.add().wrap(e));
    return this;
  }
}
