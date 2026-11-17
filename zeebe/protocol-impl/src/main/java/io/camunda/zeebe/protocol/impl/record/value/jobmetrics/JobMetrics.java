/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricsValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class JobMetrics extends MsgpackBufferValue<JobMetrics> implements JobMetricsValue {

  int jobTypeIndex = -1;
  int tenantIdIndex = -1;
  int workerNameIndex = -1;
  final List<StatusMetrics> statusMetrics = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public int getJobTypeIndex() {
    return jobTypeIndex;
  }

  public JobMetrics setJobTypeIndex(final int jobTypeIndex) {
    this.jobTypeIndex = jobTypeIndex;
    return this;
  }

  @Override
  public int getTenantIdIndex() {
    return tenantIdIndex;
  }

  public JobMetrics setTenantIdIndex(final int tenantIdIndex) {
    this.tenantIdIndex = tenantIdIndex;
    return this;
  }

  @Override
  public int getWorkerNameIndex() {
    return workerNameIndex;
  }

  @Override
  public List<StatusMetricsValue> getStatusMetrics() {
    return statusMetrics.stream().collect(Collectors.toList());
  }

  public JobMetrics setStatusMetrics(final List<StatusMetricsValue> statusMetrics) {
    this.statusMetrics.clear();
    statusMetrics.forEach(
        statusMetricsValue -> {
          final var item = new StatusMetrics();
          item.wrap(statusMetricsValue);
          this.statusMetrics.add(item);
        });
    return this;
  }

  public JobMetrics setWorkerNameIndex(final int workerNameIndex) {
    this.workerNameIndex = workerNameIndex;
    return this;
  }

  public JobMetrics wrap(final JobMetricsValue value) {
    setJobTypeIndex(value.getJobTypeIndex());
    setTenantIdIndex(value.getTenantIdIndex());
    setWorkerNameIndex(value.getWorkerNameIndex());
    setStatusMetrics(value.getStatusMetrics());
    return this;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public boolean isEmpty() {
    return false;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public int getEncodedLength() {
    return getLength();
  }

  @Override
  protected MsgpackCodec<JobMetrics> codec() {
    return JobMetricsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobMetrics> ops() {
    return JobMetricsOps.INSTANCE;
  }
}
