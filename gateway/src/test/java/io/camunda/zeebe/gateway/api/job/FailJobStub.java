/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;

public class FailJobStub extends JobRequestStub
    implements RequestStub<BrokerFailJobRequest, BrokerResponse<JobRecord>> {

  @Override
  public BrokerResponse<JobRecord> handle(final BrokerFailJobRequest request) throws Exception {
    final JobRecord responseValue = buildDefaultValue();

    responseValue.setRetries(request.getRequestWriter().getRetries());
    return new BrokerResponse<>(responseValue, 0, request.getKey());
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerFailJobRequest.class, this);
  }
}
