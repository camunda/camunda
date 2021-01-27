/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.RequestMapper;
import io.zeebe.gateway.grpc.ServerStreamObserver;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;
import org.slf4j.Logger;

public final class LongPollingActivateJobsRequest {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private final BrokerActivateJobsRequest request;
  private final ServerStreamObserver<ActivateJobsResponse> responseObserver;
  private final String jobType;
  private final String worker;
  private final int maxJobsToActivate;
  private final Duration longPollingTimeout;

  private ScheduledTimer scheduledTimer;
  private boolean isTimedOut;
  private boolean isCompleted;

  public LongPollingActivateJobsRequest(
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    this(
        RequestMapper.toActivateJobsRequest(request),
        responseObserver,
        request.getType(),
        request.getWorker(),
        request.getMaxJobsToActivate(),
        request.getRequestTimeout());
  }

  private LongPollingActivateJobsRequest(
      final BrokerActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final String jobType,
      final String worker,
      final int maxJobstoActivate,
      final long longPollingTimeout) {
    this.request = request;
    this.responseObserver = responseObserver;
    this.jobType = jobType;
    this.worker = worker;
    maxJobsToActivate = maxJobstoActivate;
    this.longPollingTimeout =
        longPollingTimeout == 0 ? null : Duration.ofMillis(longPollingTimeout);
  }

  public void complete() {
    if (isCompleted() || isCanceled()) {
      return;
    }
    if (scheduledTimer != null) {
      scheduledTimer.cancel();
    }
    try {
      responseObserver.onCompleted();
    } catch (final Exception e) {
      LOG.warn("Failed to complete {}", request, e);
    }
    isCompleted = true;
  }

  public boolean isCompleted() {
    return isCompleted;
  }

  public void onResponse(final ActivateJobsResponse grpcResponse) {
    if (!(isCompleted() || isCanceled())) {
      try {
        responseObserver.onNext(grpcResponse);
      } catch (final Exception e) {
        LOG.warn("Failed to send response to client.", e);
      }
    }
  }

  public void onError(final Throwable error) {
    if (isCompleted() || isCanceled()) {
      return;
    }

    try {
      responseObserver.onError(error);
    } catch (final Exception e) {
      LOG.warn("Failed to send response to client.", e);
    }
  }

  public void timeout() {
    complete();
    isTimedOut = true;
  }

  public boolean isCanceled() {
    return responseObserver.isCancelled();
  }

  public BrokerActivateJobsRequest getRequest() {
    return request;
  }

  public ServerStreamObserver<ActivateJobsResponse> getResponseObserver() {
    return responseObserver;
  }

  public String getType() {
    return jobType;
  }

  public String getWorker() {
    return worker;
  }

  public int getMaxJobsToActivate() {
    return maxJobsToActivate;
  }

  public void setScheduledTimer(final ScheduledTimer scheduledTimer) {
    this.scheduledTimer = scheduledTimer;
  }

  public boolean hasScheduledTimer() {
    return scheduledTimer != null;
  }

  public boolean isTimedOut() {
    return isTimedOut;
  }

  public Duration getLongPollingTimeout(final Duration defaultTimeout) {
    if (longPollingTimeout == null) {
      return defaultTimeout;
    }
    return longPollingTimeout;
  }

  public boolean isLongPollingDisabled() {
    return longPollingTimeout != null && longPollingTimeout.isNegative();
  }
}
