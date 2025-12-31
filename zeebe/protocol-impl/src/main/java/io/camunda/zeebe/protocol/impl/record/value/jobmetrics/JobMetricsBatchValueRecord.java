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
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsBatchValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Represents the metrics value for a specific job type and tenant combination. Contains job type
 * level counters and per-worker counters.
 */
public final class JobMetricsBatchValueRecord extends UnpackedObject
    implements JobMetricsBatchValue {

  private final IntegerProperty jobTypeIndexProperty = new IntegerProperty("jobTypeIndex", -1);
  private final IntegerProperty tenantIdIndexProperty = new IntegerProperty("tenantIdIndex", -1);
  private final ArrayProperty<StatusMetric> jobTypeCountersProperty =
      new ArrayProperty<>("jobTypeCounters", StatusMetric::new);
  private final ArrayProperty<JobWorkerCountersRecord> jobWorkerCountersProperty =
      new ArrayProperty<>("jobWorkerCounters", JobWorkerCountersRecord::new);

  public JobMetricsBatchValueRecord() {
    super(4);
    declareProperty(jobTypeIndexProperty);
    declareProperty(tenantIdIndexProperty);
    declareProperty(jobTypeCountersProperty);
    declareProperty(jobWorkerCountersProperty);
  }

  public int getJobTypeIndex() {
    return jobTypeIndexProperty.getValue();
  }

  public JobMetricsBatchValueRecord setJobTypeIndex(final int index) {
    jobTypeIndexProperty.setValue(index);
    return this;
  }

  public int getTenantIdIndex() {
    return tenantIdIndexProperty.getValue();
  }

  public JobMetricsBatchValueRecord setTenantIdIndex(final int index) {
    tenantIdIndexProperty.setValue(index);
    return this;
  }

  @Override
  public List<StatusMetricValue> getJobTypeCounters() {
    return StreamSupport.stream(jobTypeCountersProperty.spliterator(), false)
        .map(m -> (StatusMetricValue) m)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, List<StatusMetricValue>> getJobWorkerCounters() {
    final Map<String, List<StatusMetricValue>> result = new HashMap<>();
    for (final JobWorkerCountersRecord worker : jobWorkerCountersProperty) {
      result.put(String.valueOf(worker.getWorkerNameIndex()), worker.getCounters());
    }
    return result;
  }

  public JobMetricsBatchValueRecord addJobTypeCounter(final StatusMetric counter) {
    jobTypeCountersProperty.add().copyFrom(counter);
    return this;
  }

  public JobMetricsBatchValueRecord addJobTypeCounter(final StatusMetricValue counter) {
    final StatusMetric metric = jobTypeCountersProperty.add();
    metric.setCount(counter.getCount());
    metric.setLastUpdatedAt(counter.getLastUpdatedAt());
    return this;
  }

  public JobMetricsBatchValueRecord addWorkerCounters(final JobWorkerCountersRecord counters) {
    jobWorkerCountersProperty.add().copyFrom(counters);
    return this;
  }

  public void copyFrom(final JobMetricsBatchValueRecord other) {
    jobTypeIndexProperty.setValue(other.getJobTypeIndex());
    tenantIdIndexProperty.setValue(other.getTenantIdIndex());

    jobTypeCountersProperty.reset();
    StreamSupport.stream(other.jobTypeCountersProperty.spliterator(), false)
        .forEach(otherMetric -> jobTypeCountersProperty.add().copyFrom(otherMetric));

    jobWorkerCountersProperty.reset();
    StreamSupport.stream(other.jobWorkerCountersProperty.spliterator(), false)
        .forEach(otherWorker -> jobWorkerCountersProperty.add().copyFrom(otherWorker));
  }
}
