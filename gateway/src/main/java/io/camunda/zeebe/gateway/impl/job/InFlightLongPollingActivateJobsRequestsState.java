/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InFlightLongPollingActivateJobsRequestsState {

  private final String jobType;
  private final LongPollingMetrics metrics;
  private final Queue<InflightActivateJobsRequest> activeRequests = new LinkedList<>();
  private final Queue<InflightActivateJobsRequest> pendingRequests = new LinkedList<>();
  private final Set<InflightActivateJobsRequest> activeRequestsToBeRepeated = new HashSet<>();
  private int failedAttempts;
  private long lastUpdatedTime;

  private final AtomicBoolean ongoingNotification = new AtomicBoolean(false);

  public InFlightLongPollingActivateJobsRequestsState(
      final String jobType, final LongPollingMetrics metrics) {
    this.jobType = jobType;
    this.metrics = metrics;
  }

  public void incrementFailedAttempts(final long lastUpdatedTime) {
    failedAttempts++;
    this.lastUpdatedTime = lastUpdatedTime;
  }

  public boolean shouldAttempt(final int attemptThreshold) {
    return failedAttempts < attemptThreshold;
  }

  public void resetFailedAttempts() {
    setFailedAttempts(0);
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public void setFailedAttempts(final int failedAttempts) {
    this.failedAttempts = failedAttempts;
    if (failedAttempts == 0) {
      activeRequestsToBeRepeated.addAll(activeRequests);
    }
  }

  public long getLastUpdatedTime() {
    return lastUpdatedTime;
  }

  public void enqueueRequest(final InflightActivateJobsRequest request) {
    if (!pendingRequests.contains(request)) {
      pendingRequests.offer(request);
    }
    removeObsoleteRequestsAndUpdateMetrics();
  }

  public Queue<InflightActivateJobsRequest> getPendingRequests() {
    removeObsoleteRequestsAndUpdateMetrics();
    return pendingRequests;
  }

  private void removeObsoleteRequestsAndUpdateMetrics() {
    pendingRequests.removeIf(this::isObsolete);
    activeRequests.removeIf(this::isObsolete);
    activeRequestsToBeRepeated.removeIf(this::isObsolete);
    metrics.setBlockedRequestsCount(jobType, pendingRequests.size());
  }

  private boolean isObsolete(final InflightActivateJobsRequest request) {
    return request.isTimedOut() || request.isCanceled() || request.isCompleted();
  }

  public void removeRequest(final InflightActivateJobsRequest request) {
    pendingRequests.remove(request);
    removeObsoleteRequestsAndUpdateMetrics();
  }

  public InflightActivateJobsRequest getNextPendingRequest() {
    removeObsoleteRequestsAndUpdateMetrics();
    final InflightActivateJobsRequest request = pendingRequests.poll();
    metrics.setBlockedRequestsCount(jobType, pendingRequests.size());
    return request;
  }

  public void addActiveRequest(final InflightActivateJobsRequest request) {
    activeRequests.offer(request);
    pendingRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
  }

  public void removeActiveRequest(final InflightActivateJobsRequest request) {
    activeRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
  }

  public boolean hasActiveRequests() {
    removeObsoleteRequestsAndUpdateMetrics();
    return !activeRequests.isEmpty();
  }

  /**
   * Returns whether the request should be repeated. A request should be repeated if the failed
   * attempts were reset to 0 (because new jobs became available) whilst the request was running,
   * and if the request's long polling is enabled.
   */
  public boolean shouldBeRepeated(final InflightActivateJobsRequest request) {
    return activeRequestsToBeRepeated.contains(request) && !request.isLongPollingDisabled();
  }

  public boolean shouldNotifyAndStartNotification() {
    return ongoingNotification.compareAndSet(false, true);
  }

  public void completeNotification() {
    ongoingNotification.set(false);
  }
}
