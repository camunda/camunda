/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Can handle an 'activate jobs' request from a client. */
public interface ActivateJobsHandler extends Consumer<ActorControl> {

  static final AtomicLong ACTIVATE_JOBS_REQUEST_ID_GENERATOR = new AtomicLong(1);

  /**
   * Handle activate jobs request from a client
   *
   * @param request The request to handle
   * @param responseObserver The stream to write the responses to
   */
  void activateJobs(
      ActivateJobsRequest request, ServerStreamObserver<ActivateJobsResponse> responseObserver);

  public static InflightActivateJobsRequest toInflightActivateJobsRequest(
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    return new InflightActivateJobsRequest(
        ACTIVATE_JOBS_REQUEST_ID_GENERATOR.getAndIncrement(), request, responseObserver);
  }
}
