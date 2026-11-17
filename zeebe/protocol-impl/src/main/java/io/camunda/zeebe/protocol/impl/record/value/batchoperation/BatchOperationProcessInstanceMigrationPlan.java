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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class BatchOperationProcessInstanceMigrationPlan
    extends MsgpackBufferValue<BatchOperationProcessInstanceMigrationPlan>
    implements BatchOperationProcessInstanceMigrationPlanValue {

  long targetProcessDefinitionKey = -1;
  final List<ProcessInstanceMigrationMappingInstruction> mappingInstructions = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKey;
  }

  public BatchOperationProcessInstanceMigrationPlan setTargetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    this.targetProcessDefinitionKey = targetProcessDefinitionKey;
    return this;
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasMappingInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceMigrationMappingInstructionValue> getMappingInstructions() {
    return mappingInstructions.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceMigrationMappingInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceMigrationMappingInstructionValue) elementCopy;
            })
        .toList();
  }

  /** Returns true if this record has mapping instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMappingInstructions() {
    return !mappingInstructions.isEmpty();
  }

  public BatchOperationProcessInstanceMigrationPlan addMappingInstruction(
      final ProcessInstanceMigrationMappingInstruction mappingInstruction) {
    final var copy = new ProcessInstanceMigrationMappingInstruction();
    copy.copy(mappingInstruction);
    mappingInstructions.add(copy);
    return this;
  }

  public BatchOperationProcessInstanceMigrationPlan wrap(
      final BatchOperationProcessInstanceMigrationPlanValue record) {
    setTargetProcessDefinitionKey(record.getTargetProcessDefinitionKey());
    record
        .getMappingInstructions()
        .forEach(inst -> addMappingInstruction((ProcessInstanceMigrationMappingInstruction) inst));
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
  protected MsgpackCodec<BatchOperationProcessInstanceMigrationPlan> codec() {
    return BatchOperationProcessInstanceMigrationPlanCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationProcessInstanceMigrationPlan> ops() {
    return BatchOperationProcessInstanceMigrationPlanOps.INSTANCE;
  }
}
