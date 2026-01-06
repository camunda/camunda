/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import java.util.List;

public final class ProcessInstanceMigrationRecord extends UnifiedRecordValue
    implements ProcessInstanceMigrationRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue TARGET_PROCESS_DEFINITION_KEY_KEY =
      new StringValue("targetProcessDefinitionKey");
  private static final StringValue MAPPING_INSTRUCTIONS_KEY =
      new StringValue("mappingInstructions");

  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty targetProcessDefinitionKeyProperty =
      new LongProperty(TARGET_PROCESS_DEFINITION_KEY_KEY);
  private final ArrayProperty<ProcessInstanceMigrationMappingInstruction>
      mappingInstructionsProperty =
          new ArrayProperty<>(
              MAPPING_INSTRUCTIONS_KEY, ProcessInstanceMigrationMappingInstruction::new);

  public ProcessInstanceMigrationRecord() {
    super(3);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(targetProcessDefinitionKeyProperty)
        .declareProperty(mappingInstructionsProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceMigrationRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceMigrationRecord setTargetProcessDefinitionKey(
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
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
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

  public ProcessInstanceMigrationRecord addMappingInstruction(
      final ProcessInstanceMigrationMappingInstruction mappingInstruction) {
    mappingInstructionsProperty.add().copy(mappingInstruction);
    return this;
  }
}
