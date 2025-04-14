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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import java.util.List;

public final class BatchOperationProcessInstanceMigrationPlan extends ObjectValue
    implements BatchOperationProcessInstanceMigrationPlanValue {

  private final LongProperty targetProcessDefinitionKeyProperty =
      new LongProperty("targetProcessDefinitionKey", -1);
  private final ArrayProperty<ProcessInstanceMigrationMappingInstruction>
      mappingInstructionsProperty =
          new ArrayProperty<>(
              "mappingInstructions", ProcessInstanceMigrationMappingInstruction::new);

  public BatchOperationProcessInstanceMigrationPlan() {
    super(2);
    declareProperty(targetProcessDefinitionKeyProperty)
        .declareProperty(mappingInstructionsProperty);
  }

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKeyProperty.getValue();
  }

  public BatchOperationProcessInstanceMigrationPlan setTargetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    targetProcessDefinitionKeyProperty.setValue(targetProcessDefinitionKey);
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
    return mappingInstructionsProperty.stream()
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
    return !mappingInstructionsProperty.isEmpty();
  }

  public BatchOperationProcessInstanceMigrationPlan addMappingInstruction(
      final ProcessInstanceMigrationMappingInstruction mappingInstruction) {
    mappingInstructionsProperty.add().copy(mappingInstruction);
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
}
