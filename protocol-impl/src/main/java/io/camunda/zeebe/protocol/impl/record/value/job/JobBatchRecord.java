/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobBatchRecord extends UnifiedRecordValue implements JobBatchRecordValue {

  private final StringProperty typeProp = new StringProperty("type");
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final LongProperty timeoutProp = new LongProperty("timeout", -1);
  private final IntegerProperty maxJobsToActivateProp =
      new IntegerProperty("maxJobsToActivate", -1);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>("jobKeys", new LongValue());
  private final ArrayProperty<JobRecord> jobsProp = new ArrayProperty<>("jobs", new JobRecord());
  private final ArrayProperty<StringValue> variablesProp =
      new ArrayProperty<>("variables", new StringValue());
  private final BooleanProperty truncatedProp = new BooleanProperty("truncated", false);

  public JobBatchRecord() {
    declareProperty(typeProp)
        .declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(maxJobsToActivateProp)
        .declareProperty(jobKeysProp)
        .declareProperty(jobsProp)
        .declareProperty(variablesProp)
        .declareProperty(truncatedProp);
  }

  public JobBatchRecord setType(final DirectBuffer buf, final int offset, final int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public JobBatchRecord setWorker(final DirectBuffer worker, final int offset, final int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public ValueArray<LongValue> jobKeys() {
    return jobKeysProp;
  }

  public ValueArray<JobRecord> jobs() {
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
              final JobRecord copiedRecord = new JobRecord();

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

  public JobBatchRecord setTruncated(final boolean truncated) {
    truncatedProp.setValue(truncated);
    return this;
  }

  public JobBatchRecord setMaxJobsToActivate(final int maxJobsToActivate) {
    maxJobsToActivateProp.setValue(maxJobsToActivate);
    return this;
  }

  public JobBatchRecord setTimeout(final long val) {
    timeoutProp.setValue(val);
    return this;
  }

  public JobBatchRecord setWorker(final DirectBuffer worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecord setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecord setType(final DirectBuffer buf) {
    typeProp.setValue(buf);
    return this;
  }

  public JobBatchRecord setType(final String type) {
    typeProp.setValue(type);
    return this;
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
