/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Represents a single status metric containing a count and the timestamp when it was last updated.
 */
public final class StatusMetric extends UnpackedObject {

  private final IntegerProperty countProperty = new IntegerProperty("count", 0);
  private final LongProperty lastUpdatedAtProperty = new LongProperty("lastUpdatedAt", 0L);

  public StatusMetric() {
    super(2);
    declareProperty(countProperty);
    declareProperty(lastUpdatedAtProperty);
  }

  public int getCount() {
    return countProperty.getValue();
  }

  public void setCount(final int count) {
    countProperty.setValue(count);
  }

  public void incrementCount() {
    countProperty.setValue(countProperty.getValue() + 1);
  }

  public long getLastUpdatedAt() {
    return lastUpdatedAtProperty.getValue();
  }

  public void setLastUpdatedAt(final long lastUpdatedAt) {
    lastUpdatedAtProperty.setValue(lastUpdatedAt);
  }

  @Override
  public void reset() {
    countProperty.setValue(0);
    lastUpdatedAtProperty.setValue(0L);
  }

  public void copyFrom(final StatusMetric other) {
    countProperty.setValue(other.getCount());
    lastUpdatedAtProperty.setValue(other.getLastUpdatedAt());
  }
}
