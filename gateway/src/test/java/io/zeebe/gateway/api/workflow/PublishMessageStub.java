/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;

public class PublishMessageStub
    implements RequestStub<BrokerPublishMessageRequest, BrokerResponse<Void>> {

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerPublishMessageRequest.class, this);
  }

  @Override
  public BrokerResponse<Void> handle(BrokerPublishMessageRequest request) throws Exception {
    return new BrokerResponse<>(null);
  }
}
