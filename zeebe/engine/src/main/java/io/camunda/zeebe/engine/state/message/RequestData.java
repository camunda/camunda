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
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;

public class RequestData extends UnpackedObject implements DbValue {

  private final LongProperty requestIdProp = new LongProperty("requestId");
  private final IntegerProperty requestStreamIdProp = new IntegerProperty("requestStreamId");
  // Defaults to the null value so entries persisted before this property existed (which don't
  // carry it) read back as "no operation reference" instead of failing to deserialize.
  private final LongProperty operationReferenceProp =
      new LongProperty(
          "operationReference", RecordMetadataDecoder.operationReferenceNullValue());

  public RequestData() {
    super(3);
    declareProperty(requestIdProp)
        .declareProperty(requestStreamIdProp)
        .declareProperty(operationReferenceProp);
  }

  public RequestData copy() {
    final var copy = new RequestData();
    copy.setRequestIdProp(getRequestId());
    copy.setRequestStreamIdProp(getRequestStreamId());
    copy.setOperationReferenceProp(getOperationReference());
    return copy;
  }

  public Long getRequestId() {
    return requestIdProp.getValue();
  }

  public RequestData setRequestIdProp(final long requestId) {
    requestIdProp.setValue(requestId);
    return this;
  }

  public Integer getRequestStreamId() {
    return requestStreamIdProp.getValue();
  }

  public RequestData setRequestStreamIdProp(final int requestStreamId) {
    requestStreamIdProp.setValue(requestStreamId);
    return this;
  }

  public Long getOperationReference() {
    return operationReferenceProp.getValue();
  }

  public RequestData setOperationReferenceProp(final long operationReference) {
    operationReferenceProp.setValue(operationReference);
    return this;
  }
}
