/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ActivatedJobImpl extends MsgpackBufferValue<ActivatedJobImpl>
    implements ActivatedJob {

  @MsgpackProperty("key")
  long jobKey = -1;

  @MsgpackProperty("record")
  final JobRecord jobRecord = new JobRecord();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long jobKey() {
    return jobKey;
  }

  @Override
  public JobRecord jobRecord() {
    return jobRecord;
  }

  public ActivatedJobImpl setJobKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public ActivatedJobImpl setRecord(final JobRecord jobRecord) {
    this.jobRecord.copyFrom(jobRecord);
    return this;
  }

  @Override
  protected MsgpackCodec<ActivatedJobImpl> codec() {
    return ActivatedJobImplCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ActivatedJobImpl> ops() {
    return ActivatedJobImplOps.INSTANCE;
  }
}
