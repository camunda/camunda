/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class BatchOperationProcessInstanceModificationMoveInstruction extends ObjectValue
    implements BatchOperationCreationRecordValue.ProcessInstanceModificationMoveInstructionValue {

  private final StringProperty sourceElementIdProperty = new StringProperty("sourceElementId", "");
  private final StringProperty targetElementIdProperty = new StringProperty("targetElementId", "");

  public BatchOperationProcessInstanceModificationMoveInstruction() {
    super(2);
    declareProperty(sourceElementIdProperty).declareProperty(targetElementIdProperty);
  }

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(sourceElementIdProperty.getValue());
  }

  public BatchOperationProcessInstanceModificationMoveInstruction setSourceElementId(
      final String sourceElementId) {
    sourceElementIdProperty.setValue(sourceElementId);
    return this;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(targetElementIdProperty.getValue());
  }

  public BatchOperationProcessInstanceModificationMoveInstruction setTargetElementId(
      final String targetElementId) {
    targetElementIdProperty.setValue(targetElementId);
    return this;
  }

  public BatchOperationProcessInstanceModificationMoveInstruction copy(
      final BatchOperationProcessInstanceModificationMoveInstruction other) {
    sourceElementIdProperty.setValue(other.getSourceElementId());
    targetElementIdProperty.setValue(other.getTargetElementId());

    return this;
  }
}
