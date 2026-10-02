/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class CheckpointRecord extends UnifiedRecordValue implements CheckpointRecordValue {

  private static final String CHECKPOINT_ID_KEY = "id";
  private static final String CHECKPOINT_POSITION_KEY = "position";

  private final LongProperty checkpointIdProperty = new LongProperty(CHECKPOINT_ID_KEY, -1L);
  private final LongProperty checkpointPositionProperty =
      new LongProperty(CHECKPOINT_POSITION_KEY, -1L);
  private final StringProperty snapshotIdProperty = new StringProperty("snapshotId", "");

  public CheckpointRecord() {
    super(3);
    declareProperty(checkpointIdProperty)
        .declareProperty(checkpointPositionProperty)
        .declareProperty(snapshotIdProperty);
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
  public String getSnapshotId() {
    return BufferUtil.bufferAsString(snapshotIdProperty.getValue());
  }

  public CheckpointRecord setSnapshotId(final String snapshotId) {
    snapshotIdProperty.setValue(snapshotId);
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
