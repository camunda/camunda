/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceModificationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import java.util.List;

public final class BatchOperationProcessInstanceModificationPlan extends ObjectValue
    implements BatchOperationProcessInstanceModificationPlanValue {

  private final ArrayProperty<BatchOperationProcessInstanceModificationMoveInstruction>
      moveInstructionsProperty =
          new ArrayProperty<>(
              "moveInstructions", BatchOperationProcessInstanceModificationMoveInstruction::new);

  public BatchOperationProcessInstanceModificationPlan() {
    super(1);
    declareProperty(moveInstructionsProperty);
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasMoveInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions() {
    return moveInstructionsProperty.stream()
        .map(
            element ->
                (ProcessInstanceModificationMoveInstructionValue)
                    new BatchOperationProcessInstanceModificationMoveInstruction().copy(element))
        .toList();
  }

  /** Returns true if this record has mapping instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMoveInstructions() {
    return !moveInstructionsProperty.isEmpty();
  }

  public BatchOperationProcessInstanceModificationPlan addMoveInstruction(
      final BatchOperationProcessInstanceModificationMoveInstruction mappingInstruction) {
    moveInstructionsProperty.add().copy(mappingInstruction);
    return this;
  }

  public BatchOperationProcessInstanceModificationPlan wrap(
      final BatchOperationProcessInstanceModificationPlanValue record) {
    record
        .getMoveInstructions()
        .forEach(
            inst ->
                addMoveInstruction(
                    (BatchOperationProcessInstanceModificationMoveInstruction) inst));
    return this;
  }
}
