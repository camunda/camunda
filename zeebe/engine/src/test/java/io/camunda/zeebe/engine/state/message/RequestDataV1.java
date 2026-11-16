/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class RequestDataV1 extends UnpackedObject implements DbValue {

  private final LongProperty requestIdProp = new LongProperty("requestId");
  private final IntegerProperty requestStreamIdProp = new IntegerProperty("requestStreamId");

  public RequestDataV1() {
    super(2);
    declareProperty(requestIdProp).declareProperty(requestStreamIdProp);
  }

  public RequestDataV1 copy() {
    final var copy = new RequestDataV1();
    copy.setRequestIdProp(getRequestId());
    copy.setRequestStreamIdProp(getRequestStreamId());
    return copy;
  }

  public Long getRequestId() {
    return requestIdProp.getValue();
  }

  public RequestDataV1 setRequestIdProp(final long requestId) {
    requestIdProp.setValue(requestId);
    return this;
  }

  public Integer getRequestStreamId() {
    return requestStreamIdProp.getValue();
  }

  public RequestDataV1 setRequestStreamIdProp(final int requestStreamId) {
    requestStreamIdProp.setValue(requestStreamId);
    return this;
  }
}
