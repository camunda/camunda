/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;

public class JobResult extends UnpackedObject {

  private final BooleanProperty approvedProp = new BooleanProperty("approved", true);

  public JobResult() {
    super(1);
    declareProperty(approvedProp);
  }

  /** Sets all properties to current instance from provided user task job data */
  public void setFields(final JobResult result) {
    approvedProp.setValue(result.isApproved());
  }

  public boolean isApproved() {
    return approvedProp.getValue();
  }

  public JobResult setApproved(final boolean approved) {
    approvedProp.setValue(approved);
    return this;
  }
}
