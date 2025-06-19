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

public class RuntimeInstructions extends UnpackedObject implements DbValue {

  private final ArrayProperty<SuspendProcessInstanceRuntimeInstruction> runtimeInstructions =
      new ArrayProperty<>("instructions", SuspendProcessInstanceRuntimeInstruction::new);

  public RuntimeInstructions() {
    super(1);
    declareProperty(runtimeInstructions);
  }

  public List<SuspendProcessInstanceRuntimeInstruction> getRuntimeInstructions() {
    return StreamSupport.stream(runtimeInstructions.spliterator(), false).toList();
  }

  public void setRuntimeInstructions(
      final List<ProcessInstanceCreationRuntimeInstructionValue> instructions) {
    runtimeInstructions.reset();
    instructions.forEach(
        instruction ->
            runtimeInstructions.add().setAfterElementId(instruction.getAfterElementId()));
  }
}
