/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

/**
 * Full metadata for a single checkpoint, stored in the CHECKPOINTS column family. Tracks all
 * checkpoint types (MARKER, SCHEDULED_BACKUP, MANUAL_BACKUP).
 */
public final class CheckpointMetadataValue extends UnpackedObject implements DbValue {

  private final LongProperty checkpointPositionProperty =
      new LongProperty("checkpointPosition", -1L);
  private final LongProperty checkpointTimestampProperty =
      new LongProperty("checkpointTimestamp", -1L);
  private final EnumProperty<CheckpointType> checkpointTypeProperty =
      new EnumProperty<>("checkpointType", CheckpointType.class);
  private final LongProperty firstLogPositionProperty = new LongProperty("firstLogPosition", -1L);

  public CheckpointMetadataValue() {
    super(4);
    declareProperty(checkpointPositionProperty)
        .declareProperty(checkpointTimestampProperty)
        .declareProperty(checkpointTypeProperty)
        .declareProperty(firstLogPositionProperty);
  }

  public long getCheckpointPosition() {
    return checkpointPositionProperty.getValue();
  }

  public CheckpointMetadataValue setCheckpointPosition(final long position) {
    checkpointPositionProperty.setValue(position);
    return this;
  }

  public long getCheckpointTimestamp() {
    return checkpointTimestampProperty.getValue();
  }

  public CheckpointMetadataValue setCheckpointTimestamp(final long timestamp) {
    checkpointTimestampProperty.setValue(timestamp);
    return this;
  }

  public CheckpointType getCheckpointType() {
    return checkpointTypeProperty.getValue();
  }

  public CheckpointMetadataValue setCheckpointType(final CheckpointType type) {
    checkpointTypeProperty.setValue(type);
    return this;
  }

  public long getFirstLogPosition() {
    return firstLogPositionProperty.getValue();
  }

  public CheckpointMetadataValue setFirstLogPosition(final long firstLogPosition) {
    firstLogPositionProperty.setValue(firstLogPosition);
    return this;
  }

  @Override
  public void copyTo(final DbValue target) {
    super.copyTo((CheckpointMetadataValue) target);
  }

  @Override
  public CheckpointMetadataValue newInstance() {
    return new CheckpointMetadataValue();
  }
}
