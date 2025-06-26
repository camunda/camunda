/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class BatchOperationError extends ObjectValue implements BatchOperationErrorValue {

  private final IntegerProperty partitionProp =
      new IntegerProperty("partition", 0);
  private final EnumProperty<BatchOperationErrorType> errorTypeProp =
      new EnumProperty<>("errorType", BatchOperationErrorType.class);
  private final StringProperty stacktraceProp = new StringProperty("stacktrace");

  public BatchOperationError() {
    super(3);
    declareProperty(partitionProp)
        .declareProperty(errorTypeProp)
        .declareProperty(stacktraceProp);
  }

  public BatchOperationError wrap(final BatchOperationErrorValue record) {
    setPartition(record.getPartition());
    setErrorType(record.getErrorType());
    setStacktrace(record.getStacktrace());
    return this;
  }

  @Override
  public int getPartition() {
    return partitionProp.getValue();
  }

  public BatchOperationError setPartition(final int partition) {
    partitionProp.setValue(partition);
    return this;
  }

  @Override
  public BatchOperationErrorType getErrorType() {
    return errorTypeProp.getValue();
  }

  public BatchOperationError setErrorType(final BatchOperationErrorType errorType) {
    errorTypeProp.setValue(errorType);
    return this;
  }

  @Override
  public String getStacktrace() {
    return BufferUtil.bufferAsString(stacktraceProp.getValue());
  }

  public BatchOperationError setStacktrace(final String stacktrace) {
    stacktraceProp.setValue(stacktrace);
    return this;
  }
}
