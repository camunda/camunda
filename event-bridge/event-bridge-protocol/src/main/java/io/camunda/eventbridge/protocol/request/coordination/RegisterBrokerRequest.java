/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * A broker's request to (re-)register with the metadata-group leader. The broker proposes its node
 * id and a startup incarnation; the leader assigns a fresh, monotonic broker epoch (returned in the
 * {@link RegisterBrokerResponse}) that fences heartbeats from an earlier incarnation.
 */
public class RegisterBrokerRequest extends UnpackedObject {

  private final IntegerProperty brokerIdProp = new IntegerProperty("brokerId", -1);
  private final LongProperty incarnationProp = new LongProperty("incarnation", 0L);

  public RegisterBrokerRequest() {
    super(2);
    declareProperty(brokerIdProp).declareProperty(incarnationProp);
  }

  public int getBrokerId() {
    return brokerIdProp.getValue();
  }

  public RegisterBrokerRequest setBrokerId(final int brokerId) {
    brokerIdProp.setValue(brokerId);
    return this;
  }

  public long getIncarnation() {
    return incarnationProp.getValue();
  }

  public RegisterBrokerRequest setIncarnation(final long incarnation) {
    incarnationProp.setValue(incarnation);
    return this;
  }
}
