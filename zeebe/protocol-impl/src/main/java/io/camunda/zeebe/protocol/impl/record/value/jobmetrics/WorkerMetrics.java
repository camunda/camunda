/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.WorkerMetricsValue;
import java.util.ArrayList;
import java.util.List;

public final class WorkerMetrics extends UnpackedObject implements WorkerMetricsValue {

  private final IntegerProperty workerNameIndexProperty =
      new IntegerProperty("workerNameIndex", -1);
  private final ArrayProperty<StatusMetric> countersProperty =
      new ArrayProperty<>("counters", StatusMetric::new);

  public WorkerMetrics() {
    super(2);
    declareProperty(workerNameIndexProperty);
    declareProperty(countersProperty);
  }

  public int getWorkerNameIndex() {
    return workerNameIndexProperty.getValue();
  }

  public WorkerMetrics setWorkerNameIndex(final int workerNameIndex) {
    workerNameIndexProperty.setValue(workerNameIndex);
    return this;
  }

  @Override
  public List<StatusMetricValue> getCounters() {
    final List<StatusMetricValue> result = new ArrayList<>();
    for (final StatusMetric record : countersProperty) {
      result.add(record);
    }
    return result;
  }
}
