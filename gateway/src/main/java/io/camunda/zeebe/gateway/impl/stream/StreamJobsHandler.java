/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobsControl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

public class StreamJobsHandler extends Actor {
  private final ClientStreamer<JobActivationProperties> jobStreamer;

  public StreamJobsHandler(final ClientStreamer<JobActivationProperties> jobStreamer) {
    this.jobStreamer = jobStreamer;
  }

  public StreamObserver<StreamJobsControl> handle(
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    return new StreamJobsObserver(actor, jobStreamer, responseObserver);
  }
}
