/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceModificationPlanValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class BatchOperationProcessInstanceModificationPlan
    extends MsgpackBufferValue<BatchOperationProcessInstanceModificationPlan>
    implements BatchOperationProcessInstanceModificationPlanValue {

  final List<ProcessInstanceModificationMoveInstruction> moveInstructions = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasMoveInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions() {
    return moveInstructions.stream()
        .map(
            element ->
                (ProcessInstanceModificationMoveInstructionValue)
                    new ProcessInstanceModificationMoveInstruction().copy(element))
        .toList();
  }

  /** Returns true if this record has mapping instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMoveInstructions() {
    return !moveInstructions.isEmpty();
  }

  public BatchOperationProcessInstanceModificationPlan addMoveInstruction(
      final ProcessInstanceModificationMoveInstruction mappingInstruction) {
    moveInstructions.add(new ProcessInstanceModificationMoveInstruction().copy(mappingInstruction));
    return this;
  }

  public BatchOperationProcessInstanceModificationPlan wrap(
      final BatchOperationProcessInstanceModificationPlanValue record) {
    record
        .getMoveInstructions()
        .forEach(inst -> addMoveInstruction((ProcessInstanceModificationMoveInstruction) inst));
    return this;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public boolean isEmpty() {
    return false;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public int getEncodedLength() {
    return getLength();
  }

  @Override
  protected MsgpackCodec<BatchOperationProcessInstanceModificationPlan> codec() {
    return BatchOperationProcessInstanceModificationPlanCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationProcessInstanceModificationPlan> ops() {
    return BatchOperationProcessInstanceModificationPlanOps.INSTANCE;
  }
}
