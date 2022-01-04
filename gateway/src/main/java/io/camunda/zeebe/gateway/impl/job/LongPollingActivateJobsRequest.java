/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;

public final class LongPollingActivateJobsRequest {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private final long requestId;
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
      final long requestId,
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    this(
        requestId,
        RequestMapper.toActivateJobsRequest(request),
        responseObserver,
        request.getType(),
        request.getWorker(),
        request.getMaxJobsToActivate(),
        request.getRequestTimeout());
  }

  private LongPollingActivateJobsRequest(
      final long requestId,
      final BrokerActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final String jobType,
      final String worker,
      final int maxJobsToActivate,
      final long longPollingTimeout) {
    this.requestId = requestId;
    this.request = request;
    this.responseObserver = responseObserver;
    this.jobType = jobType;
    this.worker = worker;
    this.maxJobsToActivate = maxJobsToActivate;
    this.longPollingTimeout =
        longPollingTimeout == 0 ? null : Duration.ofMillis(longPollingTimeout);
  }

  public void complete() {
    if (isCompleted() || isCanceled()) {
      return;
    }
    cancelTimerIfScheduled();
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
    cancelTimerIfScheduled();
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

  private void cancelTimerIfScheduled() {
    if (hasScheduledTimer()) {
      scheduledTimer.cancel();
      scheduledTimer = null;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobType, maxJobsToActivate, requestId, worker);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final var other = (LongPollingActivateJobsRequest) obj;
    return Objects.equals(jobType, other.jobType)
        && maxJobsToActivate == other.maxJobsToActivate
        && requestId == other.requestId
        && Objects.equals(worker, other.worker);
  }
}
