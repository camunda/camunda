/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class RuntimeInstructionsV1 extends UnpackedObject implements DbValue {

  private final ArrayProperty<RuntimeInstructionValueV1> runtimeInstructions =
      new ArrayProperty<>("instructions", RuntimeInstructionValueV1::new);

  public RuntimeInstructionsV1() {
    super(1);
    declareProperty(runtimeInstructions);
  }

  public List<RuntimeInstructionValueV1> getRuntimeInstructions() {
    return StreamSupport.stream(runtimeInstructions.spliterator(), false).toList();
  }

  public void setRuntimeInstructions(
      final List<ProcessInstanceCreationRuntimeInstructionValue> instructions) {
    runtimeInstructions.reset();
    instructions.forEach(
        instruction -> {
          final var newInstruction = runtimeInstructions.add();
          newInstruction.setType(instruction.getType());
          newInstruction.setAfterElementId(instruction.getAfterElementId());
        });
  }
}
