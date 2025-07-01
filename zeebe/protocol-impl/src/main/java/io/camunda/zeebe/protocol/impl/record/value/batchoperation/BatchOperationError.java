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

  private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId", 0);
  private final EnumProperty<BatchOperationErrorType> typeProp =
      new EnumProperty<>("type", BatchOperationErrorType.class, BatchOperationErrorType.UNKNOWN);
  private final StringProperty messageProp = new StringProperty("message", "");

  public BatchOperationError() {
    super(3);
    declareProperty(partitionIdProp);
    declareProperty(typeProp);
    declareProperty(messageProp);
  }

  public BatchOperationError wrap(final BatchOperationErrorValue record) {
    setPartitionId(record.getPartitionId());
    setType(record.getType());
    setMessage(record.getMessage());
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionIdProp.getValue();
  }

  public BatchOperationError setPartitionId(final int partitionId) {
    partitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public BatchOperationErrorType getType() {
    return typeProp.getValue();
  }

  public BatchOperationError setType(final BatchOperationErrorType type) {
    typeProp.setValue(type);
    return this;
  }

  @Override
  public String getMessage() {
    return BufferUtil.bufferAsString(messageProp.getValue());
  }

  public BatchOperationError setMessage(final String message) {
    messageProp.setValue(message);
    return this;
  }
}
