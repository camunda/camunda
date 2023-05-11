/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;

public class ActivatedJobImpl extends UnpackedObject implements ActivatedJob {

  private final LongProperty jobKey = new LongProperty("key", -1);
  private final ObjectProperty<JobRecord> jobRecord =
      new ObjectProperty<>("record", new JobRecord());

  public ActivatedJobImpl() {
    declareProperty(jobKey).declareProperty(jobRecord);
  }

  @Override
  public long jobKey() {
    return jobKey.getValue();
  }

  @Override
  public JobRecord jobRecord() {
    return jobRecord.getValue();
  }

  public ActivatedJobImpl setJobKey(final long jobKey) {
    this.jobKey.setValue(jobKey);
    return this;
  }

  public ActivatedJobImpl setRecord(final JobRecord jobRecord) {
    this.jobRecord.getValue().wrap(jobRecord);
    return this;
  }
}
