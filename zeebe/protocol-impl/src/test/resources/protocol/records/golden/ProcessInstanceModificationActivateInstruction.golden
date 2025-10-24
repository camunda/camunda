/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessInstanceModificationActivateInstruction extends ObjectValue
    implements ProcessInstanceModificationActivateInstructionValue {

  private final StringProperty elementIdProperty = new StringProperty("elementId");
  private final LongProperty ancestorScopeKeyProperty = new LongProperty("ancestorScopeKey", -1);
  private final ArrayProperty<ProcessInstanceModificationVariableInstruction>
      variableInstructionsProperty =
          new ArrayProperty<>(
              "variableInstructions", ProcessInstanceModificationVariableInstruction::new);

  private final ArrayProperty<LongValue> ancestorScopeKeysProperty =
      new ArrayProperty<>("ancestorScopeKeys", LongValue::new);

  public ProcessInstanceModificationActivateInstruction() {
    super(4);
    declareProperty(elementIdProperty)
        .declareProperty(ancestorScopeKeyProperty)
        .declareProperty(variableInstructionsProperty)
        .declareProperty(ancestorScopeKeysProperty);
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  @Override
  public long getAncestorScopeKey() {
    return ancestorScopeKeyProperty.getValue();
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasVariableInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationVariableInstructionValue> getVariableInstructions() {
    return variableInstructionsProperty.stream()
        .map(
            instruction -> {
              final var copy = new ProcessInstanceModificationVariableInstruction();
              copy.copy(instruction);
              return (ProcessInstanceModificationVariableInstructionValue) copy;
            })
        .toList();
  }

  @Override
  public Set<Long> getAncestorScopeKeys() {
    return ancestorScopeKeysProperty.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  public ProcessInstanceModificationActivateInstruction setAncestorScopeKey(
      final long ancestorScopeKey) {
    ancestorScopeKeyProperty.setValue(ancestorScopeKey);
    return this;
  }

  public ProcessInstanceModificationActivateInstruction setElementId(final String elementId) {
    elementIdProperty.setValue(elementId);
    return this;
  }

  /** Returns true if this record has variable instructions, otherwise false. */
  @JsonIgnore
  public boolean hasVariableInstructions() {
    return !variableInstructionsProperty.isEmpty();
  }

  public ProcessInstanceModificationActivateInstruction addVariableInstruction(
      final ProcessInstanceModificationVariableInstruction variableInstruction) {
    variableInstructionsProperty.add().copy(variableInstruction);
    return this;
  }

  public ProcessInstanceModificationActivateInstruction addAncestorScopeKeys(
      final Set<Long> flowScopeKeys) {
    flowScopeKeys.forEach(key -> ancestorScopeKeysProperty.add().setValue(key));
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProperty.getValue();
  }

  public void copy(final ProcessInstanceModificationActivateInstruction object) {
    setElementId(object.getElementId());
    setAncestorScopeKey(object.getAncestorScopeKey());
    object.getVariableInstructions().stream()
        .map(ProcessInstanceModificationVariableInstruction.class::cast)
        .forEach(this::addVariableInstruction);
    addAncestorScopeKeys(object.getAncestorScopeKeys());
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
