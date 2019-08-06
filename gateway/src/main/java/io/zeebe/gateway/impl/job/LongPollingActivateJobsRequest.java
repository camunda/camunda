/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.RequestMapper;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.util.sched.ScheduledTimer;
import org.slf4j.Logger;

public class LongPollingActivateJobsRequest {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private final BrokerActivateJobsRequest request;
  private final StreamObserver<ActivateJobsResponse> responseObserver;
  private final String jobType;
  private final int maxJobsToActivate;

  private ScheduledTimer scheduledTimer;
  private boolean isTimedOut;
  private boolean isCompleted;

  public LongPollingActivateJobsRequest(
      ActivateJobsRequest request, StreamObserver<ActivateJobsResponse> responseObserver) {
    this(
        RequestMapper.toActivateJobsRequest(request),
        responseObserver,
        request.getType(),
        request.getMaxJobsToActivate());
  }

  private LongPollingActivateJobsRequest(
      BrokerActivateJobsRequest request,
      StreamObserver<ActivateJobsResponse> responseObserver,
      String jobType,
      int maxJobstoActivate) {
    this.request = request;
    this.responseObserver = responseObserver;
    this.jobType = jobType;
    this.maxJobsToActivate = maxJobstoActivate;
  }

  public void complete() {
    if (isCompleted()) {
      return;
    }
    if (scheduledTimer != null) {
      scheduledTimer.cancel();
    }
    try {
      responseObserver.onCompleted();
    } catch (Exception e) {
      LOG.warn("Failed to complete {}", request, e);
    }
    this.isCompleted = true;
  }

  public boolean isCompleted() {
    return this.isCompleted;
  }

  public void onResponse(ActivateJobsResponse grpcResponse) {
    if (!isCompleted) {
      try {
        responseObserver.onNext(grpcResponse);
      } catch (Exception e) {
        LOG.warn("Failed to send response {}", e);
      }
    }
  }

  public void timeout() {
    complete();
    this.isTimedOut = true;
  }

  public BrokerActivateJobsRequest getRequest() {
    return request;
  }

  public StreamObserver<ActivateJobsResponse> getResponseObserver() {
    return responseObserver;
  }

  public String getType() {
    return jobType;
  }

  public int getMaxJobsToActivate() {
    return maxJobsToActivate;
  }

  public void setScheduledTimer(ScheduledTimer scheduledTimer) {
    this.scheduledTimer = scheduledTimer;
  }

  public boolean hasScheduledTimer() {
    return scheduledTimer != null;
  }

  public boolean isTimedOut() {
    return isTimedOut;
  }
}
