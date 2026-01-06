/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricValue;

public final class StatusMetric extends UnpackedObject implements StatusMetricValue {

  private final IntegerProperty countProperty = new IntegerProperty("count", 0);
  private final LongProperty lastUpdatedAtProperty = new LongProperty("lastUpdatedAt", 0L);

  public StatusMetric() {
    super(2);
    declareProperty(countProperty);
    declareProperty(lastUpdatedAtProperty);
  }

  @Override
  public int getCount() {
    return countProperty.getValue();
  }

  @Override
  public long getLastUpdatedAt() {
    return lastUpdatedAtProperty.getValue();
  }

  public StatusMetric setLastUpdatedAt(final long lastUpdatedAt) {
    lastUpdatedAtProperty.setValue(lastUpdatedAt);
    return this;
  }

  public StatusMetric setCount(final int count) {
    countProperty.setValue(count);
    return this;
  }

  public void copyFrom(final StatusMetricValue other) {
    countProperty.setValue(other.getCount());
    lastUpdatedAtProperty.setValue(other.getLastUpdatedAt());
  }
}
