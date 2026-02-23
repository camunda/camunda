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

/** Checkpoint info stored in db in msgpack format. */
public final class CheckpointInfo extends UnpackedObject implements DbValue {
  private final LongProperty idProperty = new LongProperty("id");
  private final LongProperty positionProperty = new LongProperty("position");
  private final LongProperty timestamp = new LongProperty("timestamp");
  private final EnumProperty<CheckpointType> typeProperty =
      new EnumProperty<>("type", CheckpointType.class, CheckpointType.MANUAL_BACKUP);
  private final LongProperty firstLogPositionProperty = new LongProperty("firstLogPosition", -1L);

  public CheckpointInfo() {
    super(5);
    declareProperty(idProperty)
        .declareProperty(positionProperty)
        .declareProperty(timestamp)
        .declareProperty(typeProperty)
        .declareProperty(firstLogPositionProperty);
  }

  public long getId() {
    return idProperty.getValue();
  }

  public CheckpointInfo setId(final long id) {
    idProperty.setValue(id);
    return this;
  }

  public long getPosition() {
    return positionProperty.getValue();
  }

  public CheckpointInfo setPosition(final long position) {
    positionProperty.setValue(position);
    return this;
  }

  public long getTimestamp() {
    return timestamp.getValue();
  }

  public CheckpointInfo setTimestamp(final long timestamp) {
    this.timestamp.setValue(timestamp);
    return this;
  }

  public CheckpointType getType() {
    return typeProperty.getValue();
  }

  public CheckpointInfo setType(final CheckpointType type) {
    typeProperty.setValue(type);
    return this;
  }

  public long getFirstLogPosition() {
    return firstLogPositionProperty.getValue();
  }

  public CheckpointInfo setFirstLogPosition(final long firstLogPosition) {
    firstLogPositionProperty.setValue(firstLogPosition);
    return this;
  }
}
