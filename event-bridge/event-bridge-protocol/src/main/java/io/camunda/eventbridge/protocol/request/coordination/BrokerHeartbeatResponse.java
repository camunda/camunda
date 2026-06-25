/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;

/**
 * The metadata leader's reply to a {@link BrokerHeartbeatRequest}. {@code errorCode} is {@code
 * FENCED_MEMBER_EPOCH} when the presented epoch is stale (the broker must re-register), else {@code
 * NONE}. {@code shouldShutdown} acknowledges a drain request once the broker's replicas have moved
 * off, so the broker may stop.
 */
public class BrokerHeartbeatResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final BooleanProperty shouldShutdownProp = new BooleanProperty("shouldShutdown", false);

  public BrokerHeartbeatResponse() {
    super(2);
    declareProperty(errorCodeProp).declareProperty(shouldShutdownProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public BrokerHeartbeatResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public boolean getShouldShutdown() {
    return shouldShutdownProp.getValue();
  }

  public BrokerHeartbeatResponse setShouldShutdown(final boolean shouldShutdown) {
    shouldShutdownProp.setValue(shouldShutdown);
    return this;
  }
}
