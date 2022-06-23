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
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;

public class InflightActivateJobsRequest {

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
  private boolean isAborted;

  public InflightActivateJobsRequest(
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

  private InflightActivateJobsRequest(
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
    if (!isOpen()) {
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

  /**
   * Sends activated jobs to the respective client.
   *
   * @param activatedJobs to send back to the client
   * @return an instance of {@link Either} indicating the following:
   *     <ul>
   *       <li>{@link Either#get() == true}: if the activated jobs have been sent back to the client
   *       <li>{@link Either#get() == false}: if the activated jobs couldn't be sent back to the
   *           client
   *       <li>{@link Either#getLeft() != null}: if sending back the activated jobs failed with an
   *           exception (note: in this case {@link Either#isRight() == false})
   *     </ul>
   */
  public Either<Exception, Boolean> tryToSendActivatedJobs(
      final ActivateJobsResponse activatedJobs) {
    if (isOpen()) {
      try {
        responseObserver.onNext(activatedJobs);
        return Either.right(true);
      } catch (final Exception e) {
        LOG.warn("Failed to send response to client.", e);
        return Either.left(e);
      }
    }
    return Either.right(false);
  }

  public void onError(final Throwable error) {
    if (!isOpen()) {
      return;
    }
    cancelTimerIfScheduled();
    try {
      responseObserver.onError(error);
    } catch (final Exception e) {
      LOG.warn("Failed to send terminating error to client.", e);
    }
    isAborted = true;
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

  public boolean isAborted() {
    return isAborted;
  }

  public boolean isOpen() {
    return !(isCompleted() || isCanceled() || isAborted());
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
    final var other = (InflightActivateJobsRequest) obj;
    return Objects.equals(jobType, other.jobType)
        && maxJobsToActivate == other.maxJobsToActivate
        && requestId == other.requestId
        && Objects.equals(worker, other.worker);
  }
}
