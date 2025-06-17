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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class SuspendProcessInstanceRuntimeInstruction extends UnpackedObject
    implements DbValue {

  private final StringProperty afterElementIdProp = new StringProperty("afterElementId", "");

  public SuspendProcessInstanceRuntimeInstruction() {
    super(1);
    declareProperty(afterElementIdProp);
  }

  public void setAfterElementId(final String afterElementId) {
    afterElementIdProp.setValue(afterElementId);
  }

  public String getAfterElementId() {
    return BufferUtil.bufferAsString(afterElementIdProp.getValue());
  }
}
