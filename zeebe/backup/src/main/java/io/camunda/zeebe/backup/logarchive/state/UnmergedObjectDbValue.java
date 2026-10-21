/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Value of {@code LOG_ARCHIVE_UNMERGED_OBJECTS}, keyed by the object's first index: what the merge
 * rules need to know about an archived object above M, without the rest of the object.
 */
final class UnmergedObjectDbValue extends UnpackedObject implements DbValue {

  private final LongProperty lastIndexProperty = new LongProperty("lastIndex", -1L);
  private final LongProperty lastPositionProperty = new LongProperty("lastPosition", -1L);

  UnmergedObjectDbValue() {
    super(2);
    declareProperty(lastIndexProperty).declareProperty(lastPositionProperty);
  }

  long lastIndex() {
    return lastIndexProperty.getValue();
  }

  long lastPosition() {
    return lastPositionProperty.getValue();
  }

  UnmergedObjectDbValue set(final long lastIndex, final long lastPosition) {
    lastIndexProperty.setValue(lastIndex);
    lastPositionProperty.setValue(lastPosition);
    return this;
  }
}
