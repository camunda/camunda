/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.gateway.api.util.StubbedBrokerClient;
import io.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.job.JobRecord;

public final class ThrowErrorStub extends JobRequestStub
    implements RequestStub<BrokerThrowErrorRequest, BrokerResponse<JobRecord>> {

  @Override
  public BrokerResponse<JobRecord> handle(final BrokerThrowErrorRequest request) throws Exception {
    final JobRecord responseValue = buildDefaultValue();

    final var errorCode = request.getRequestWriter().getErrorCode();
    responseValue.setErrorCode(wrapString(errorCode));
    return new BrokerResponse<>(responseValue, 0, request.getKey());
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerThrowErrorRequest.class, this);
  }
}
