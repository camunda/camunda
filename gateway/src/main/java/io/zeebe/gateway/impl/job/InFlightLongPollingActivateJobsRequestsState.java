/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.metrics.LongPollingMetrics;
import java.util.LinkedList;
import java.util.Queue;
import org.slf4j.Logger;

public final class InFlightLongPollingActivateJobsRequestsState {

  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final String jobType;
  private final LongPollingMetrics metrics;
  private final Queue<LongPollingActivateJobsRequest> activeRequests = new LinkedList<>();
  private final Queue<LongPollingActivateJobsRequest> activeRequestsToBeRepeated =
      new LinkedList<>();
  private final Queue<LongPollingActivateJobsRequest> pendingRequests = new LinkedList<>();
  private int failedAttempts;
  private long lastUpdatedTime;

  public InFlightLongPollingActivateJobsRequestsState(
      final String jobType, final LongPollingMetrics metrics) {
    this.jobType = jobType;
    this.metrics = metrics;
  }

  public void incrementFailedAttempts(final long lastUpdatedTime) {
    failedAttempts++;
    this.lastUpdatedTime = lastUpdatedTime;
  }

  public void setFailedAttempts(final int failedAttempts) {
    this.failedAttempts = failedAttempts;
    if (failedAttempts == 0) {
      activeRequestsToBeRepeated.addAll(activeRequests);
    }
  }

  public void resetFailedAttempts() {
    setFailedAttempts(0);
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public long getLastUpdatedTime() {
    return lastUpdatedTime;
  }

  public void enqueueRequest(final LongPollingActivateJobsRequest request) {
    if (!pendingRequests.contains(request)) {
      pendingRequests.offer(request);
    }
    removeObsoleteRequestsAndUpdateMetrics();
  }

  public Queue<LongPollingActivateJobsRequest> getPendingRequests() {
    removeObsoleteRequestsAndUpdateMetrics();
    return pendingRequests;
  }

  private void removeObsoleteRequestsAndUpdateMetrics() {
    pendingRequests.removeIf(this::isObsolete);
    activeRequests.removeIf(this::isObsolete);
    activeRequestsToBeRepeated.removeIf(this::isObsolete);
    metrics.setBlockedRequestsCount(jobType, pendingRequests.size());
  }

  private boolean isObsolete(final LongPollingActivateJobsRequest request) {
    return request.isTimedOut() || request.isCanceled() || request.isCompleted();
  }

  public void removeRequest(final LongPollingActivateJobsRequest request) {
    pendingRequests.remove(request);
    removeObsoleteRequestsAndUpdateMetrics();
  }

  public LongPollingActivateJobsRequest getNextPendingRequest() {
    removeObsoleteRequestsAndUpdateMetrics();
    final LongPollingActivateJobsRequest request = pendingRequests.poll();
    metrics.setBlockedRequestsCount(jobType, pendingRequests.size());
    return request;
  }

  public void addActiveRequest(final LongPollingActivateJobsRequest request) {
    activeRequests.offer(request);
    pendingRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
  }

  public void removeActiveRequest(final LongPollingActivateJobsRequest request) {
    activeRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
  }

  public boolean hasActiveRequests() {
    removeObsoleteRequestsAndUpdateMetrics();
    return !activeRequests.isEmpty();
  }

  /**
   * Returns whether the request should be repeated. A request should be repeated if the failed
   * attempts were reset to 0 (because new jobs became available) whilst the request was running
   */
  public boolean shouldBeRepeated(final LongPollingActivateJobsRequest request) {
    return activeRequestsToBeRepeated.contains(request);
  }
}
