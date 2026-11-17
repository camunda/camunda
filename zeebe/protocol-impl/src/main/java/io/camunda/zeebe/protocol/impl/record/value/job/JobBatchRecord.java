/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class JobBatchRecord extends MsgpackBufferValue<JobBatchRecord>
    implements MsgpackRecordValue, JobBatchRecordValue {

  @Nullable DirectBuffer type;
  DirectBuffer worker = Decoding.EMPTY;
  long timeout = -1;
  int maxJobsToActivate = -1;
  final List<LongValue> jobKeys = new ArrayList<>();
  final List<JobRecord> jobs = new ArrayList<>();
  final List<StringValue> variables = new ArrayList<>();
  boolean truncated = false;
  boolean withLease = false;
  final List<StringValue> tenantIds = new ArrayList<>();
  TenantFilter tenantFilter = TenantFilter.PROVIDED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public JobBatchRecord setType(final DirectBuffer buf, final int offset, final int length) {
    type = copy(buf, offset, length);
    return this;
  }

  public JobBatchRecord setWorker(final DirectBuffer worker, final int offset, final int length) {
    this.worker = copy(worker, offset, length);
    return this;
  }

  public ValueArray<LongValue> jobKeys() {
    return new ListValueArray<>(jobKeys, LongValue::new);
  }

  public ValueArray<JobRecord> jobs() {
    return new ListValueArray<>(jobs, JobRecord::new);
  }

  public ValueArray<StringValue> variables() {
    return new ListValueArray<>(variables, StringValue::new);
  }

  public boolean getTruncated() {
    return truncated;
  }

  @Override
  public String getType() {
    return BufferUtil.bufferAsString(getTypeBuffer());
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(worker);
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  @Override
  public int getMaxJobsToActivate() {
    return maxJobsToActivate;
  }

  @Override
  public List<Long> getJobKeys() {
    return jobKeys.stream().map(LongValue::getValue).collect(Collectors.toList());
  }

  @Override
  public List<JobRecordValue> getJobs() {
    return jobs.stream()
        .map(
            jobRecord -> {
              final var copiedRecord = new JobRecord();
              copiedRecord.copyFrom(jobRecord);
              return (JobRecordValue) copiedRecord;
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean isTruncated() {
    return truncated;
  }

  @Override
  public boolean isWithLease() {
    return withLease;
  }

  @Override
  public List<String> getTenantIds() {
    return tenantIds.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  @Override
  public TenantFilter getTenantFilter() {
    return tenantFilter;
  }

  public JobBatchRecord setTenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
    return this;
  }

  public JobBatchRecord setTenantIds(final List<String> tenantIds) {
    this.tenantIds.clear();
    tenantIds.forEach(tenantId -> this.tenantIds.add(new StringValue(tenantId)));
    return this;
  }

  public JobBatchRecord setTruncated(final boolean truncated) {
    this.truncated = truncated;
    return this;
  }

  public JobBatchRecord setWithLease(final boolean withLease) {
    this.withLease = withLease;
    return this;
  }

  public JobBatchRecord setMaxJobsToActivate(final int maxJobsToActivate) {
    this.maxJobsToActivate = maxJobsToActivate;
    return this;
  }

  public JobBatchRecord setTimeout(final long val) {
    timeout = val;
    return this;
  }

  public JobBatchRecord setWorker(final DirectBuffer worker) {
    return setWorker(worker, 0, worker.capacity());
  }

  public JobBatchRecord setWorker(final String worker) {
    this.worker = BufferUtil.wrapString(worker);
    return this;
  }

  public JobBatchRecord setType(final DirectBuffer buf) {
    return setType(buf, 0, buf.capacity());
  }

  public JobBatchRecord setType(final String type) {
    this.type = BufferUtil.wrapString(type);
    return this;
  }

  public ValueArray<StringValue> tenantIds() {
    return new ListValueArray<>(tenantIds, StringValue::new);
  }

  @JsonIgnore
  public DirectBuffer getTypeBuffer() {
    return Decoding.required(type, "type");
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return worker;
  }

  // setters detach the value: callers hand in buffers they reuse afterwards
  private static DirectBuffer copy(final DirectBuffer source, final int offset, final int length) {
    final var bytes = new byte[length];
    source.getBytes(offset, bytes);
    return new UnsafeBuffer(bytes);
  }

  @Override
  protected MsgpackCodec<JobBatchRecord> codec() {
    return JobBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobBatchRecord> ops() {
    return JobBatchRecordOps.INSTANCE;
  }

  private static final class ListValueArray<T> implements ValueArray<T> {

    final List<T> elements;
    final Supplier<T> factory;

    private ListValueArray(final List<T> elements, final Supplier<T> factory) {
      this.elements = elements;
      this.factory = factory;
    }

    @Override
    public T add() {
      return add(elements.size());
    }

    @Override
    public T add(final int index) {
      final var element = factory.get();
      elements.add(index, element);
      return element;
    }

    @Override
    public T get(final int index) {
      return elements.get(index);
    }

    @Override
    public T remove(final int index) {
      return elements.remove(index);
    }

    @Override
    public int size() {
      return elements.size();
    }

    @Override
    public Stream<T> stream() {
      return elements.stream();
    }

    @Override
    public Iterator<T> iterator() {
      return elements.iterator();
    }
  }
}
