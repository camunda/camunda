/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.grpc.ServerStreamObserver;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;

/** Can handle an 'activate jobs' request from a client. */
public interface ActivateJobsHandler {

  /**
   * Handle activate jobs request from a client
   *
   * @param request The request to handle
   * @param responseObserver The stream to write the responses to
   */
  void activateJobs(
      ActivateJobsRequest request, ServerStreamObserver<ActivateJobsResponse> responseObserver);
}
