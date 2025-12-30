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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Represents the value stored in the JobMetrics column family. Contains: - jobTypeCounters:
 * StatusMetric[State.size] (each State will correspond to one index) - jobWorkerCounters:
 * Map&lt;workerName, StatusMetric[State.size]&gt;
 */
public final class JobMetricsValue extends UnpackedObject implements DbValue {

  private final ArrayProperty<StatusMetric> jobTypeCountersProperty =
      new ArrayProperty<>("jobTypeCounters", StatusMetric::new);
  private final ArrayProperty<JobWorkerCounters> jobWorkerCountersProperty =
      new ArrayProperty<>("jobWorkerCounters", JobWorkerCounters::new);

  public JobMetricsValue() {
    super(2);
    declareProperty(jobTypeCountersProperty);
    declareProperty(jobWorkerCountersProperty);
    initializeJobTypeCounters();
  }

  private void initializeJobTypeCounters() {
    for (int i = 0; i < JobMetricState.STATE_COUNT; i++) {
      jobTypeCountersProperty.add().reset();
    }
  }

  /**
   * Gets the status metric for the given job state at the job type level.
   *
   * @param state the job metric state
   * @return the status metric for the given state
   */
  public StatusMetric getJobTypeCounter(final JobMetricState state) {
    int index = 0;
    for (final StatusMetric metric : jobTypeCountersProperty) {
      if (index == state.getIndex()) {
        return metric;
      }
      index++;
    }
    return null;
  }

  /**
   * Increments the counter for the given job state at the job type level.
   *
   * @param state the job metric state
   * @param timestamp the timestamp when this update occurred
   */
  public void incrementJobTypeCounter(final JobMetricState state, final long timestamp) {
    final StatusMetric metric = getJobTypeCounter(state);
    if (metric != null) {
      metric.incrementCount();
      metric.setLastUpdatedAt(timestamp);
    }
  }

  /**
   * Gets or creates worker counters for the given worker name.
   *
   * @param workerName the name of the worker
   * @return the counters for the worker
   */
  public JobWorkerCounters getOrCreateWorkerCounters(final String workerName) {
    for (final JobWorkerCounters worker : jobWorkerCountersProperty) {
      if (worker.getWorkerName().equals(workerName)) {
        return worker;
      }
    }
    // Create new worker counters
    final JobWorkerCounters newWorker = jobWorkerCountersProperty.add();
    newWorker.setWorkerName(workerName);
    return newWorker;
  }

  /**
   * Gets the counters for the given worker name, or null if not found.
   *
   * @param workerName the name of the worker
   * @return the counters for the worker, or null if not found
   */
  public JobWorkerCounters getWorkerCounters(final String workerName) {
    for (final JobWorkerCounters worker : jobWorkerCountersProperty) {
      if (worker.getWorkerName().equals(workerName)) {
        return worker;
      }
    }
    return null;
  }

  /**
   * Increments the counter for the given job state at the worker level.
   *
   * @param workerName the name of the worker
   * @param state the job metric state
   * @param timestamp the timestamp when this update occurred
   */
  public void incrementWorkerCounter(
      final String workerName, final JobMetricState state, final long timestamp) {
    final JobWorkerCounters workerCounters = getOrCreateWorkerCounters(workerName);
    workerCounters.incrementCounter(state, timestamp);
  }

  /**
   * Gets all worker counters as a map.
   *
   * @return a map of worker name to worker counters
   */
  public Map<String, JobWorkerCounters> getAllWorkerCounters() {
    final Map<String, JobWorkerCounters> result = new HashMap<>();
    for (final JobWorkerCounters worker : jobWorkerCountersProperty) {
      result.put(worker.getWorkerName(), worker);
    }
    return result;
  }

  /** Resets all counters (job type level and worker level). */
  @Override
  public void reset() {
    jobTypeCountersProperty.reset();
    initializeJobTypeCounters();
    jobWorkerCountersProperty.reset();
  }

  public void copyFrom(final JobMetricsValue other) {
    jobTypeCountersProperty.reset();
    StreamSupport.stream(other.jobTypeCountersProperty.spliterator(), false)
        .forEach(
            otherMetric -> {
              final StatusMetric newMetric = jobTypeCountersProperty.add();
              newMetric.copyFrom(otherMetric);
            });

    jobWorkerCountersProperty.reset();
    StreamSupport.stream(other.jobWorkerCountersProperty.spliterator(), false)
        .forEach(
            otherWorker -> {
              final JobWorkerCounters newWorker = jobWorkerCountersProperty.add();
              newWorker.copyFrom(otherWorker);
            });
  }
}
