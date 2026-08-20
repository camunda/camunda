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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessInstanceModificationMoveInstruction extends ObjectValue
    implements ProcessInstanceModificationMoveInstructionValue {

  private final StringProperty sourceElementIdProperty = new StringProperty("sourceElementId", "");
  private final LongProperty sourceElementInstanceKeyProperty =
      new LongProperty("sourceElementInstanceKey", -1);
  private final StringProperty targetElementIdProperty = new StringProperty("targetElementId", "");
  private final ArrayProperty<ProcessInstanceModificationVariableInstruction>
      variableInstructionsProperty =
          new ArrayProperty<>(
              "variableInstructions", ProcessInstanceModificationVariableInstruction::new);
  private final LongProperty ancestorScopeKeyProperty = new LongProperty("ancestorScopeKey", -1);
  private final BooleanProperty inferAncestorScopeFromSourceHierarchy =
      new BooleanProperty("inferAncestorScopeFromSourceHierarchy", false);
  private final BooleanProperty useSourceParentKeyAsAncestorScopeKey =
      new BooleanProperty("useSourceParentKeyAsAncestorScopeKey", false);

  public ProcessInstanceModificationMoveInstruction() {
    super(7);
    declareProperty(sourceElementIdProperty)
        .declareProperty(sourceElementInstanceKeyProperty)
        .declareProperty(targetElementIdProperty)
        .declareProperty(variableInstructionsProperty)
        .declareProperty(ancestorScopeKeyProperty)
        .declareProperty(inferAncestorScopeFromSourceHierarchy)
        .declareProperty(useSourceParentKeyAsAncestorScopeKey);
  }

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(getSourceElementIdBuffer());
  }

  @Override
  public long getSourceElementInstanceKey() {
    return sourceElementInstanceKeyProperty.getValue();
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(getTargetElementIdBuffer());
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
  public long getAncestorScopeKey() {
    return ancestorScopeKeyProperty.getValue();
  }

  public ProcessInstanceModificationMoveInstruction setAncestorScopeKey(
      final long ancestorScopeKey) {
    ancestorScopeKeyProperty.setValue(ancestorScopeKey);
    return this;
  }

  @Override
  public boolean isInferAncestorScopeFromSourceHierarchy() {
    return inferAncestorScopeFromSourceHierarchy.getValue();
  }

  public ProcessInstanceModificationMoveInstruction setInferAncestorScopeFromSourceHierarchy(
      final boolean inferAncestorScopeFromSource) {
    inferAncestorScopeFromSourceHierarchy.setValue(inferAncestorScopeFromSource);
    return this;
  }

  @Override
  public boolean isUseSourceParentKeyAsAncestorScopeKey() {
    return useSourceParentKeyAsAncestorScopeKey.getValue();
  }

  public ProcessInstanceModificationMoveInstruction setUseSourceParentKeyAsAncestorScopeKey(
      final boolean useSourceParentKey) {
    useSourceParentKeyAsAncestorScopeKey.setValue(useSourceParentKey);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setTargetElementId(
      final String targetElementId) {
    targetElementIdProperty.setValue(targetElementId);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setSourceElementInstanceKey(
      final long sourceElementInstanceKey) {
    sourceElementInstanceKeyProperty.setValue(sourceElementInstanceKey);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setSourceElementId(
      final String sourceElementId) {
    sourceElementIdProperty.setValue(sourceElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getSourceElementIdBuffer() {
    return sourceElementIdProperty.getValue();
  }

  /** Returns true if this record has variable instructions, otherwise false. */
  @JsonIgnore
  public boolean hasVariableInstructions() {
    return !variableInstructionsProperty.isEmpty();
  }

  public ProcessInstanceModificationMoveInstruction addVariableInstruction(
      final ProcessInstanceModificationVariableInstruction variableInstruction) {
    variableInstructionsProperty.add().copy(variableInstruction);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction copy(
      final ProcessInstanceModificationMoveInstructionValue object) {
    setSourceElementId(object.getSourceElementId());
    setSourceElementInstanceKey(object.getSourceElementInstanceKey());
    setTargetElementId(object.getTargetElementId());
    object.getVariableInstructions().stream()
        .map(ProcessInstanceModificationVariableInstruction.class::cast)
        .forEach(this::addVariableInstruction);
    setAncestorScopeKey(object.getAncestorScopeKey());
    setInferAncestorScopeFromSourceHierarchy(object.isInferAncestorScopeFromSourceHierarchy());
    setUseSourceParentKeyAsAncestorScopeKey(object.isUseSourceParentKeyAsAncestorScopeKey());
    return this;
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
