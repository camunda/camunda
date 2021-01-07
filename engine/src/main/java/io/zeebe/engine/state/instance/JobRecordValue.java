/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.protocol.impl.record.value.job.JobRecord;

public class JobRecordValue extends UnpackedObject implements DbValue {

  private final ObjectProperty<JobRecord> recordProp =
      new ObjectProperty<>("jobRecord", new JobRecord());

  public JobRecordValue() {
    declareProperty(recordProp);
  }

  public JobRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecordWithoutVariables(final JobRecord record) {
    recordProp.getValue().wrapWithoutVariables(record);
  }
}
