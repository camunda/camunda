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
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.WorkerMetricsValue;
import java.util.HashMap;
import java.util.Map;

public final class JobMetrics extends UnpackedObject implements JobMetricsValue {

  private final IntegerProperty jobTypeIndexProperty = new IntegerProperty("jobTypeIndex", -1);
  private final IntegerProperty tenantIdIndexProperty = new IntegerProperty("tenantIdIndex", -1);

  private final ArrayProperty<WorkerMetrics> workerMetricsProperty =
      new ArrayProperty<>("workerMetrics", WorkerMetrics::new);

  public JobMetrics() {
    super(3);
    declareProperty(jobTypeIndexProperty);
    declareProperty(tenantIdIndexProperty);
    declareProperty(workerMetricsProperty);
  }

  public int getJobTypeIndex() {
    return jobTypeIndexProperty.getValue();
  }

  public JobMetrics setJobTypeIndex(final int jobTypeIndex) {
    jobTypeIndexProperty.setValue(jobTypeIndex);
    return this;
  }

  public int getTenantIdIndex() {
    return tenantIdIndexProperty.getValue();
  }

  public JobMetrics setTenantIdIndex(final int tenantIdIndex) {
    tenantIdIndexProperty.setValue(tenantIdIndex);
    return this;
  }

  @Override
  public Map<String, WorkerMetricsValue> getWorkerMetrics() {
    final Map<String, WorkerMetricsValue> result = new HashMap<>();
    for (final WorkerMetrics record : workerMetricsProperty) {
      final String key = String.valueOf(record.getWorkerNameIndex());
      result.put(key, record);
    }
    return result;
  }
}
