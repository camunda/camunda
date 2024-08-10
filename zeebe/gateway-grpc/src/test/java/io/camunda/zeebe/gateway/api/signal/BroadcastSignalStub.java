/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.signal;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
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
    final var requestRecord = request.getRequestWriter();
    final var responseRecord =
        new SignalRecord()
            .setSignalName(requestRecord.getSignalName())
            .setVariables(requestRecord.getVariablesBuffer())
            .setTenantId(requestRecord.getTenantId());

    return new BrokerResponse<>(responseRecord, 0, 123L);
  }
}
