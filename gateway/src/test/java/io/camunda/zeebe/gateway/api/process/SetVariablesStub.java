/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.process;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;

public final class SetVariablesStub
    implements RequestStub<BrokerSetVariablesRequest, BrokerResponse<VariableDocumentRecord>> {

  private static final long KEY = 12345L;

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerSetVariablesRequest.class, this);
  }

  public long getKey() {
    return KEY;
  }

  @Override
  public BrokerResponse<VariableDocumentRecord> handle(final BrokerSetVariablesRequest request)
      throws Exception {
    return new BrokerResponse<>(new VariableDocumentRecord(), request.getPartitionId(), KEY);
  }
}
