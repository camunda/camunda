/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class ActivatedJobImplV1 extends UnpackedObject {

  private final LongProperty jobKey = new LongProperty("key", -1);
  private final ObjectProperty<JobRecordV1> jobRecord =
      new ObjectProperty<>("record", new JobRecordV1());

  public ActivatedJobImplV1() {
    super(2);
    declareProperty(jobKey).declareProperty(jobRecord);
  }

  public long jobKey() {
    return jobKey.getValue();
  }

  public JobRecordV1 jobRecord() {
    return jobRecord.getValue();
  }

  public ActivatedJobImplV1 setJobKey(final long jobKey) {
    this.jobKey.setValue(jobKey);
    return this;
  }

  public ActivatedJobImplV1 setRecord(final JobRecordV1 jobRecord) {
    this.jobRecord.getValue().wrap(jobRecord);
    return this;
  }
}
