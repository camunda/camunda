/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.CoveredCheckpointValue;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class CoveredCheckpoint extends ObjectValue implements CoveredCheckpointValue {

  private final LongProperty checkpointIdProperty = new LongProperty("checkpointId", -1L);
  private final LongProperty checkpointPositionProperty =
      new LongProperty("checkpointPosition", -1L);
  private final EnumProperty<CheckpointType> checkpointTypeProperty =
      new EnumProperty<>("checkpointType", CheckpointType.class, CheckpointType.MANUAL_BACKUP);

  public CoveredCheckpoint() {
    super(3);
    declareProperty(checkpointIdProperty)
        .declareProperty(checkpointPositionProperty)
        .declareProperty(checkpointTypeProperty);
  }

  @Override
  public long getCheckpointId() {
    return checkpointIdProperty.getValue();
  }

  @Override
  public long getCheckpointPosition() {
    return checkpointPositionProperty.getValue();
  }

  @Override
  public CheckpointType getCheckpointType() {
    return checkpointTypeProperty.getValue();
  }

  public CoveredCheckpoint setCheckpointId(final long checkpointId) {
    checkpointIdProperty.setValue(checkpointId);
    return this;
  }

  public CoveredCheckpoint setCheckpointPosition(final long checkpointPosition) {
    checkpointPositionProperty.setValue(checkpointPosition);
    return this;
  }

  public CoveredCheckpoint setCheckpointType(final CheckpointType checkpointType) {
    checkpointTypeProperty.setValue(checkpointType);
    return this;
  }

  public CoveredCheckpoint copy(final CoveredCheckpointValue object) {
    setCheckpointId(object.getCheckpointId());
    setCheckpointPosition(object.getCheckpointPosition());
    setCheckpointType(object.getCheckpointType());
    return this;
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
