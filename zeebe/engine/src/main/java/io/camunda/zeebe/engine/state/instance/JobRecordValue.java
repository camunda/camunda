/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class JobRecordValue extends MsgpackBufferValue<JobRecordValue> implements DbValue {

  final JobRecord jobRecord = new JobRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public JobRecord getRecord() {
    return jobRecord;
  }

  public void setRecordWithoutVariables(final JobRecord record) {
    jobRecord.wrapWithoutVariables(record);
  }

  @Override
  protected MsgpackCodec<JobRecordValue> codec() {
    return JobRecordValueCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobRecordValue> ops() {
    return JobRecordValueOps.INSTANCE;
  }
}
