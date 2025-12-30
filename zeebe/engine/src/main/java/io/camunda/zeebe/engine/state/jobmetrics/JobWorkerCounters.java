/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.stream.StreamSupport;

/**
 * Represents the counters for a single job worker, containing a worker name and status metrics for
 * each job state.
 */
public final class JobWorkerCounters extends UnpackedObject {

  private final StringProperty workerNameProperty = new StringProperty("workerName", "");
  private final ArrayProperty<StatusMetric> countersProperty =
      new ArrayProperty<>("counters", StatusMetric::new);

  public JobWorkerCounters() {
    super(2);
    declareProperty(workerNameProperty);
    declareProperty(countersProperty);
    initializeCounters();
  }

  private void initializeCounters() {
    for (int i = 0; i < JobMetricState.STATE_COUNT; i++) {
      countersProperty.add().reset();
    }
  }

  public String getWorkerName() {
    return BufferUtil.bufferAsString(workerNameProperty.getValue());
  }

  public void setWorkerName(final String workerName) {
    workerNameProperty.setValue(workerName);
  }

  public StatusMetric getCounter(final JobMetricState state) {
    int index = 0;
    for (final StatusMetric metric : countersProperty) {
      if (index == state.getIndex()) {
        return metric;
      }
      index++;
    }
    return null;
  }

  public void incrementCounter(final JobMetricState state, final long timestamp) {
    final StatusMetric metric = getCounter(state);
    if (metric != null) {
      metric.incrementCount();
      metric.setLastUpdatedAt(timestamp);
    }
  }

  @Override
  public void reset() {
    workerNameProperty.setValue("");
    countersProperty.reset();
    initializeCounters();
  }

  public void copyFrom(final JobWorkerCounters other) {
    workerNameProperty.setValue(other.getWorkerName());
    countersProperty.reset();
    StreamSupport.stream(other.countersProperty.spliterator(), false)
        .forEach(
            otherMetric -> {
              final StatusMetric newMetric = countersProperty.add();
              newMetric.copyFrom(otherMetric);
            });
  }
}
