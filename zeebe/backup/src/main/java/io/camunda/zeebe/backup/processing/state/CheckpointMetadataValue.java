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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferUtil;

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
      new EnumProperty<>("checkpointType", CheckpointType.class, CheckpointType.MARKER);
  private final LongProperty firstLogPositionProperty = new LongProperty("firstLogPosition", -1L);
  private final IntegerProperty numberOfPartitionsProperty =
      new IntegerProperty("numberOfPartitions", -1);
  private final StringProperty brokerVersionProperty = new StringProperty("brokerVersion", "");

  public CheckpointMetadataValue() {
    super(6);
    declareProperty(checkpointPositionProperty)
        .declareProperty(checkpointTimestampProperty)
        .declareProperty(checkpointTypeProperty)
        .declareProperty(firstLogPositionProperty)
        .declareProperty(numberOfPartitionsProperty)
        .declareProperty(brokerVersionProperty);
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

  public int getNumberOfPartitions() {
    return numberOfPartitionsProperty.getValue();
  }

  public CheckpointMetadataValue setNumberOfPartitions(final int numberOfPartitions) {
    numberOfPartitionsProperty.setValue(numberOfPartitions);
    return this;
  }

  public String getBrokerVersion() {
    return BufferUtil.bufferAsString(brokerVersionProperty.getValue());
  }

  public CheckpointMetadataValue setBrokerVersion(final String brokerVersion) {
    brokerVersionProperty.setValue(brokerVersion);
    return this;
  }
}
