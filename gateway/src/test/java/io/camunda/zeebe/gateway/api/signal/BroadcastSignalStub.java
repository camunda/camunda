/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.signal;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;

public final class BroadcastSignalStub
    implements RequestStub<BrokerBroadcastSignalRequest, BrokerResponse<SignalRecord>> {

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerBroadcastSignalRequest.class, this);
  }

  @Override
  public BrokerResponse<SignalRecord> handle(final BrokerBroadcastSignalRequest request)
      throws Exception {
    return new BrokerResponse<>(null);
  }
}
