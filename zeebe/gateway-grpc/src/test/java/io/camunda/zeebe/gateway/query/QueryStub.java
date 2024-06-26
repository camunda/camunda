/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.query;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.query.impl.BrokerExecuteQuery;

final class QueryStub implements RequestStub<BrokerExecuteQuery, BrokerResponse<String>> {
  private final BrokerResponse<String> response;

  public QueryStub(final BrokerResponse<String> response) {
    this.response = response;
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerExecuteQuery.class, this);
  }

  @Override
  public BrokerResponse<String> handle(final BrokerExecuteQuery request) throws Exception {
    return response;
  }
}
