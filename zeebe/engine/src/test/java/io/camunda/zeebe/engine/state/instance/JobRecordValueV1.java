/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class JobRecordValueV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<JobRecordV1> recordProp =
      new ObjectProperty<>("jobRecord", new JobRecordV1());

  public JobRecordValueV1() {
    super(1);
    declareProperty(recordProp);
  }

  public JobRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public void setRecordWithoutVariables(final JobRecordV1 record) {
    recordProp.getValue().wrapWithoutVariables(record);
  }
}
