/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class RuntimeInstructions extends MsgpackBufferValue<RuntimeInstructions>
    implements DbValue {

  @MsgpackProperty("instructions")
  final List<RuntimeInstructionValue> runtimeInstructions = new ArrayList<>();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public List<RuntimeInstructionValue> getRuntimeInstructions() {
    return runtimeInstructions.stream().toList();
  }

  public void setRuntimeInstructions(
      final List<ProcessInstanceCreationRuntimeInstructionValue> instructions) {
    runtimeInstructions.clear();
    instructions.forEach(
        instruction -> {
          final var newInstruction = new RuntimeInstructionValue();
          newInstruction.setType(instruction.getType());
          newInstruction.setAfterElementId(instruction.getAfterElementId());
          runtimeInstructions.add(newInstruction);
        });
  }

  @Override
  protected MsgpackCodec<RuntimeInstructions> codec() {
    return RuntimeInstructionsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<RuntimeInstructions> ops() {
    return RuntimeInstructionsOps.INSTANCE;
  }
}
