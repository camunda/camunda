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
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * The metadata leader's reply to a {@link RegisterBrokerRequest}: the broker epoch assigned to this
 * registration, which the broker then echoes on every heartbeat so the leader can fence a stale
 * incarnation.
 */
public class RegisterBrokerResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final LongProperty brokerEpochProp = new LongProperty("brokerEpoch", -1L);

  public RegisterBrokerResponse() {
    super(2);
    declareProperty(errorCodeProp).declareProperty(brokerEpochProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public RegisterBrokerResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public long getBrokerEpoch() {
    return brokerEpochProp.getValue();
  }

  public RegisterBrokerResponse setBrokerEpoch(final long brokerEpoch) {
    brokerEpochProp.setValue(brokerEpoch);
    return this;
  }
}
