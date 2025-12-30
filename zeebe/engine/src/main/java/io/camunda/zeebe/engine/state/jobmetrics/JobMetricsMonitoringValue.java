/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Represents the value stored in the JobMetricsMonitoring column family. Contains: -
 * totalSizeBytes: when a new entry is created in the JobMetricsColumnFamily, we will update the
 * size here
 */
public final class JobMetricsMonitoringValue extends UnpackedObject implements DbValue {

  private final LongProperty totalSizeBytesProperty = new LongProperty("totalSizeBytes", 0L);

  public JobMetricsMonitoringValue() {
    super(1);
    declareProperty(totalSizeBytesProperty);
  }

  public long getTotalSizeBytes() {
    return totalSizeBytesProperty.getValue();
  }

  public void setTotalSizeBytes(final long additionalBytes) {
    totalSizeBytesProperty.setValue(additionalBytes);
  }

  public void incrementTotalSizeBytes(final long bytes) {
    totalSizeBytesProperty.setValue(totalSizeBytesProperty.getValue() + bytes);
  }

  @Override
  public void reset() {
    totalSizeBytesProperty.setValue(0L);
  }
}
