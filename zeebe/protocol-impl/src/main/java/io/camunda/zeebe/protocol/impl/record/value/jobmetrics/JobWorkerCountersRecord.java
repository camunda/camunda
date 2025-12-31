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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Represents the counters for a single job worker in protocol-impl. Contains a worker name index
 * (encoded) and status metrics for each job state.
 */
public final class JobWorkerCountersRecord extends UnpackedObject {

  private final IntegerProperty workerNameIndexProperty =
      new IntegerProperty("workerNameIndex", -1);
  private final ArrayProperty<StatusMetric> countersProperty =
      new ArrayProperty<>("counters", StatusMetric::new);

  public JobWorkerCountersRecord() {
    super(2);
    declareProperty(workerNameIndexProperty);
    declareProperty(countersProperty);
  }

  public int getWorkerNameIndex() {
    return workerNameIndexProperty.getValue();
  }

  public JobWorkerCountersRecord setWorkerNameIndex(final int index) {
    workerNameIndexProperty.setValue(index);
    return this;
  }

  public List<StatusMetricValue> getCounters() {
    return StreamSupport.stream(countersProperty.spliterator(), false)
        .map(m -> (StatusMetricValue) m)
        .collect(Collectors.toList());
  }

  public JobWorkerCountersRecord addCounter(final StatusMetric counter) {
    countersProperty.add().copyFrom(counter);
    return this;
  }

  public JobWorkerCountersRecord addCounter(final StatusMetricValue counter) {
    final StatusMetric metric = countersProperty.add();
    metric.setCount(counter.getCount());
    metric.setLastUpdatedAt(counter.getLastUpdatedAt());
    return this;
  }

  public void copyFrom(final JobWorkerCountersRecord other) {
    workerNameIndexProperty.setValue(other.getWorkerNameIndex());
    countersProperty.reset();
    StreamSupport.stream(other.countersProperty.spliterator(), false)
        .forEach(otherMetric -> countersProperty.add().copyFrom(otherMetric));
  }
}
