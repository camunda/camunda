/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler;
import io.camunda.zeebe.stream.api.GatewayStreamer.GatewayStream;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStream;

final class JobGatewayStream implements GatewayStream<JobActivationProperties, ActivatedJob> {

  private final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream;

  JobGatewayStream(final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream) {
    this.remoteStream = remoteStream;
  }

  @Override
  public JobActivationProperties metadata() {
    return remoteStream.metadata();
  }

  @Override
  public void push(final ActivatedJob p, final ErrorHandler<ActivatedJob> errorHandler) {
    remoteStream.push(p, errorHandler::handleError);
  }
}
