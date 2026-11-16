/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class JobMetricsBatchRecord extends MsgpackBufferValue<JobMetricsBatchRecord>
    implements MsgpackRecordValue, JobMetricsBatchRecordValue {

  long batchStartTime = -1L;
  long batchEndTime = -1L;
  boolean recordSizeLimitExceeded = false;

  /**
   * A pool of strings used to reduce record size by deduplication. Instead of storing the same
   * strings multiple times across different JobMetrics entries, strings are stored once in this
   * pool and referenced by index.
   *
   * <p>The indices in this pool correspond to the indices used in JobMetrics for:
   *
   * <ul>
   *   <li>jobType
   *   <li>tenantId
   *   <li>workerName
   * </ul>
   *
   * <p>For example, if "my-job-type" is at index 0 in encodedStrings, a JobMetrics entry can
   * reference it by storing 0 as its jobType index, avoiding string duplication.
   */
  final List<DirectBuffer> encodedStrings = new ArrayList<>();

  final List<JobMetrics> jobMetrics = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchStartTime() {
    return batchStartTime;
  }

  public JobMetricsBatchRecord setBatchStartTime(final long batchStartTime) {
    this.batchStartTime = batchStartTime;
    return this;
  }

  @Override
  public long getBatchEndTime() {
    return batchEndTime;
  }

  public JobMetricsBatchRecord setBatchEndTime(final long batchEndTime) {
    this.batchEndTime = batchEndTime;
    return this;
  }

  @Override
  public boolean getRecordSizeLimitExceeded() {
    return recordSizeLimitExceeded;
  }

  public JobMetricsBatchRecord setRecordSizeLimitExceeded(final boolean recordSizeLimitExceeded) {
    this.recordSizeLimitExceeded = recordSizeLimitExceeded;
    return this;
  }

  @Override
  public List<String> getEncodedStrings() {
    return encodedStrings.stream().map(BufferUtil::bufferAsString).toList();
  }

  @Override
  public List<JobMetricsValue> getJobMetrics() {
    return new ArrayList<>(jobMetrics);
  }

  public JobMetricsBatchRecord setJobMetrics(final Collection<JobMetricsValue> jobMetrics) {
    final var copies = jobMetrics.stream().map(jm -> new JobMetrics().wrap(jm)).toList();
    this.jobMetrics.clear();
    this.jobMetrics.addAll(copies);
    return this;
  }

  public JobMetricsBatchRecord setEncodedStrings(final List<String> encodedStrings) {
    this.encodedStrings.clear();
    encodedStrings.forEach(str -> this.encodedStrings.add(BufferUtil.wrapString(str)));
    return this;
  }

  public JobMetricsBatchRecord addJobMetrics(final JobMetricsValue jobMetrics) {
    this.jobMetrics.add(new JobMetrics().wrap(jobMetrics));
    return this;
  }

  @Override
  protected MsgpackCodec<JobMetricsBatchRecord> codec() {
    return JobMetricsBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobMetricsBatchRecord> ops() {
    return JobMetricsBatchRecordOps.INSTANCE;
  }
}
