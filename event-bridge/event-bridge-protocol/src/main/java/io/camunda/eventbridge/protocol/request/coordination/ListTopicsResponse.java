/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * Carries the topic registry as an encoded payload (one topic per line: {@code
 * name;partitionCount;replicationFactor;status}). Avoids a msgpack array-of-objects for this
 * low-frequency control-plane response; the gateway parses it into DTOs.
 */
public class ListTopicsResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final StringProperty payloadProp = new StringProperty("payload", "");

  public ListTopicsResponse() {
    super(2);
    declareProperty(errorCodeProp).declareProperty(payloadProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public ListTopicsResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public String getPayload() {
    return bufferAsString(payloadProp.getValue());
  }

  public ListTopicsResponse setPayload(final String payload) {
    payloadProp.setValue(payload);
    return this;
  }
}
