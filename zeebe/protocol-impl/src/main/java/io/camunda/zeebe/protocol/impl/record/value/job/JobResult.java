/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultValue;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public class JobResult extends UnpackedObject implements JobResultValue {

  private final BooleanProperty deniedProp = new BooleanProperty("denied", false);

  public JobResult() {
    super(1);
    declareProperty(deniedProp);
  }

  /** Sets all properties to current instance from provided user task job data */
  public void setFields(final JobResult result) {
    deniedProp.setValue(result.isDenied());
  }

  @Override
  public boolean isDenied() {
    return deniedProp.getValue();
  }

  public JobResult setDenied(final boolean denied) {
    deniedProp.setValue(denied);
    return this;
  }
}
