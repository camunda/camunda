/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import java.util.stream.Collectors;

public class BatchOperation extends UnpackedObject implements DbValue {

  private final ArrayProperty<LongValue> keysProp =
      new ArrayProperty<>("keys", LongValue::new);
  private final EnumProperty<BatchOperationType> typeProp =
      new EnumProperty<>("type", BatchOperationType.class);
  private final EnumProperty<BatchOperationState> statusProp =
      new EnumProperty<>("status", BatchOperationState.class);
  private final IntegerProperty offsetProp =
      new IntegerProperty("offset", 0);

  public BatchOperation() {
    super(4);
    declareProperty(keysProp);
    declareProperty(typeProp);
    declareProperty(statusProp);
    declareProperty(offsetProp);
  }

  public boolean canCancel() {
    return getStatus() == BatchOperationState.CREATED
        || getStatus() == BatchOperationState.ACTIVATED
        || getStatus() == BatchOperationState.PAUSED;
  }

  public boolean canPause() {
    return getStatus() == BatchOperationState.CREATED
        || getStatus() == BatchOperationState.ACTIVATED;
  }

  public boolean canResume() {
    return getStatus() == BatchOperationState.PAUSED;
  }

  public List<Long> getKeys() {
    return keysProp.stream().map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  public void setKeys(final List<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
  }

  public BatchOperationType getType() {
    return typeProp.getValue();
  }

  public void setType(final BatchOperationType type) {
    typeProp.setValue(type);
  }

  public BatchOperationState getStatus() {
    return statusProp.getValue();
  }

  public void setStatus(final BatchOperationState status) {
    statusProp.setValue(status);
  }

  public int getOffset() {
    return offsetProp.getValue();
  }

  public void setOffset(final int offset) {
    offsetProp.setValue(offset);
  }

  public enum BatchOperationState {
    CREATED,
    ACTIVATED,
    PAUSED,
    CANCELED,
    COMPLETED
  }

}
