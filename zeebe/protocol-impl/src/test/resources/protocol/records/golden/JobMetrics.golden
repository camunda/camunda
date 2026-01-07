/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricValue;
import java.util.List;
import java.util.stream.Collectors;

public final class JobMetrics extends ObjectValue implements JobMetricsValue {

  private final IntegerProperty jobTypeIndexProperty = new IntegerProperty("jobTypeIndex", -1);
  private final IntegerProperty tenantIdIndexProperty = new IntegerProperty("tenantIdIndex", -1);
  private final IntegerProperty workerNameIndexProperty =
      new IntegerProperty("workerNameIndex", -1);

  private final ArrayProperty<StatusMetric> statusMetricsProperty =
      new ArrayProperty<>("statusMetrics", StatusMetric::new);

  public JobMetrics() {
    super(4);
    declareProperty(jobTypeIndexProperty);
    declareProperty(tenantIdIndexProperty);
    declareProperty(workerNameIndexProperty);
    declareProperty(statusMetricsProperty);
  }

  @Override
  public int getJobTypeIndex() {
    return jobTypeIndexProperty.getValue();
  }

  public JobMetrics setJobTypeIndex(final int jobTypeIndex) {
    jobTypeIndexProperty.setValue(jobTypeIndex);
    return this;
  }

  @Override
  public int getTenantIdIndex() {
    return tenantIdIndexProperty.getValue();
  }

  public JobMetrics setTenantIdIndex(final int tenantIdIndex) {
    tenantIdIndexProperty.setValue(tenantIdIndex);
    return this;
  }

  @Override
  public int getWorkerNameIndex() {
    return workerNameIndexProperty.getValue();
  }

  @Override
  public List<StatusMetricValue> getStatusMetrics() {
    return statusMetricsProperty.stream().collect(Collectors.toList());
  }

  public JobMetrics setWorkerNameIndex(final int workerNameIndex) {
    workerNameIndexProperty.setValue(workerNameIndex);
    return this;
  }

  public JobMetrics wrap(final JobMetricsValue value) {
    setJobTypeIndex(value.getJobTypeIndex());
    setTenantIdIndex(value.getTenantIdIndex());
    setTenantIdIndex(value.getTenantIdIndex());
    setStatusMetrics(value.getStatusMetrics());
    return this;
  }

  public JobMetricsValue setStatusMetrics(final List<StatusMetricValue> statusMetrics) {
    statusMetricsProperty.reset();
    statusMetrics.forEach(
        statusMetricValue ->
            statusMetricsProperty
                .add()
                .setCount(statusMetricValue.getCount())
                .setLastUpdatedAt(statusMetricValue.getLastUpdatedAt()));
    return this;
  }
}
