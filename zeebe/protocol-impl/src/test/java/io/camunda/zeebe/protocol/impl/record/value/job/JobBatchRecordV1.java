/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class JobBatchRecordV1 extends UnifiedRecordValue implements JobBatchRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue WORKER_KEY = new StringValue("worker");
  private static final StringValue TIMEOUT_KEY = new StringValue("timeout");
  private static final StringValue MAX_JOBS_TO_ACTIVATE_KEY = new StringValue("maxJobsToActivate");
  private static final StringValue JOB_KEYS_KEY = new StringValue("jobKeys");
  private static final StringValue JOBS_KEY = new StringValue("jobs");
  private static final StringValue TENANT_IDS_KEY = new StringValue("tenantIds");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue TRUNCATED_KEY = new StringValue("truncated");
  private static final StringValue WITH_LEASE_KEY = new StringValue("withLease");
  private static final StringValue TENANT_FILTER_KEY = new StringValue("tenantFilter");

  private final StringProperty typeProp = new StringProperty(TYPE_KEY);
  private final StringProperty workerProp = new StringProperty(WORKER_KEY, "");
  private final LongProperty timeoutProp = new LongProperty(TIMEOUT_KEY, -1);
  private final IntegerProperty maxJobsToActivateProp =
      new IntegerProperty(MAX_JOBS_TO_ACTIVATE_KEY, -1);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>(JOB_KEYS_KEY, LongValue::new);
  private final ArrayProperty<JobRecordV1> jobsProp =
      new ArrayProperty<>(JOBS_KEY, JobRecordV1::new);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>(TENANT_IDS_KEY, StringValue::new);
  private final ArrayProperty<StringValue> variablesProp =
      new ArrayProperty<>(VARIABLES_KEY, StringValue::new);
  private final BooleanProperty truncatedProp = new BooleanProperty(TRUNCATED_KEY, false);
  private final BooleanProperty withLeaseProp = new BooleanProperty(WITH_LEASE_KEY, false);
  private final EnumProperty<TenantFilter> tenantFilterProp =
      new EnumProperty<>(TENANT_FILTER_KEY, TenantFilter.class, TenantFilter.PROVIDED);

  public JobBatchRecordV1() {
    super(11);
    declareProperty(typeProp)
        .declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(maxJobsToActivateProp)
        .declareProperty(jobKeysProp)
        .declareProperty(jobsProp)
        .declareProperty(variablesProp)
        .declareProperty(truncatedProp)
        .declareProperty(withLeaseProp)
        .declareProperty(tenantIdsProp)
        .declareProperty(tenantFilterProp);
  }

  public JobBatchRecordV1 setType(final DirectBuffer buf, final int offset, final int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public JobBatchRecordV1 setWorker(final DirectBuffer worker, final int offset, final int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public ValueArray<LongValue> jobKeys() {
    return jobKeysProp;
  }

  public ValueArray<JobRecordV1> jobs() {
    return jobsProp;
  }

  public ValueArray<StringValue> variables() {
    return variablesProp;
  }

  public boolean getTruncated() {
    return truncatedProp.getValue();
  }

  @Override
  public String getType() {
    return BufferUtil.bufferAsString(typeProp.getValue());
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  @Override
  public long getTimeout() {
    return timeoutProp.getValue();
  }

  @Override
  public int getMaxJobsToActivate() {
    return maxJobsToActivateProp.getValue();
  }

  @Override
  public List<Long> getJobKeys() {
    return StreamSupport.stream(jobKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  @Override
  public List<JobRecordValue> getJobs() {
    return StreamSupport.stream(jobsProp.spliterator(), false)
        .map(
            jobRecord -> {
              final byte[] bytes = new byte[jobRecord.getLength()];
              final UnsafeBuffer copyRecord = new UnsafeBuffer(bytes);
              final JobRecordV1 copiedRecord = new JobRecordV1();

              jobRecord.write(copyRecord, 0);
              copiedRecord.wrap(copyRecord);

              return copiedRecord;
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean isTruncated() {
    return truncatedProp.getValue();
  }

  @Override
  public boolean isWithLease() {
    return withLeaseProp.getValue();
  }

  @Override
  public List<String> getTenantIds() {
    return StreamSupport.stream(tenantIdsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  @Override
  public TenantFilter getTenantFilter() {
    return tenantFilterProp.getValue();
  }

  public JobBatchRecordV1 setTenantFilter(final TenantFilter tenantFilter) {
    tenantFilterProp.setValue(tenantFilter);
    return this;
  }

  public JobBatchRecordV1 setTenantIds(final List<String> tenantIds) {
    tenantIdsProp.reset();
    tenantIds.forEach(tenantId -> tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public JobBatchRecordV1 setTruncated(final boolean truncated) {
    truncatedProp.setValue(truncated);
    return this;
  }

  public JobBatchRecordV1 setWithLease(final boolean withLease) {
    withLeaseProp.setValue(withLease);
    return this;
  }

  public JobBatchRecordV1 setMaxJobsToActivate(final int maxJobsToActivate) {
    maxJobsToActivateProp.setValue(maxJobsToActivate);
    return this;
  }

  public JobBatchRecordV1 setTimeout(final long val) {
    timeoutProp.setValue(val);
    return this;
  }

  public JobBatchRecordV1 setWorker(final DirectBuffer worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecordV1 setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecordV1 setType(final DirectBuffer buf) {
    typeProp.setValue(buf);
    return this;
  }

  public JobBatchRecordV1 setType(final String type) {
    typeProp.setValue(type);
    return this;
  }

  public ValueArray<StringValue> tenantIds() {
    return tenantIdsProp;
  }

  @JsonIgnore
  public DirectBuffer getTypeBuffer() {
    return typeProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
  }
}
