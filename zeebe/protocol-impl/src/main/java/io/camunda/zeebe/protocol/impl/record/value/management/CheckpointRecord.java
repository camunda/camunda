/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

public class CheckpointRecord extends UnifiedRecordValue implements CheckpointRecordValue {

  private static final String CHECKPOINT_ID_KEY = "id";
  private static final String CHECKPOINT_POSITION_KEY = "position";
  private static final String CHECKPOINT_TYPE_KEY = "type";

  private final LongProperty checkpointIdProperty = new LongProperty(CHECKPOINT_ID_KEY, -1L);
  private final LongProperty checkpointPositionProperty =
      new LongProperty(CHECKPOINT_POSITION_KEY, -1L);
  private final EnumProperty<CheckpointType> checkpointTypeProperty =
      new EnumProperty<>(CHECKPOINT_TYPE_KEY, CheckpointType.class, CheckpointType.MANUAL_BACKUP);

  public CheckpointRecord() {
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

  public CheckpointRecord setCheckpointType(final CheckpointType checkpointType) {
    checkpointTypeProperty.setValue(checkpointType);
    return this;
  }

  public CheckpointRecord setCheckpointPosition(final long checkpointPosition) {
    checkpointPositionProperty.setValue(checkpointPosition);
    return this;
  }

  public CheckpointRecord setCheckpointId(final long checkpointId) {
    checkpointIdProperty.setValue(checkpointId);
    return this;
  }
}
