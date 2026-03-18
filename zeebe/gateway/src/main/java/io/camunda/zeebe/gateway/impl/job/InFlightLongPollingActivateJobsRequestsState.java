/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class InFlightLongPollingActivateJobsRequestsState<T> {

  private final String jobType;
  private final LongPollingMetrics metrics;
  private final Queue<InflightActivateJobsRequest<T>> activeRequests = new LinkedList<>();
  private final Queue<InflightActivateJobsRequest<T>> pendingRequests = new LinkedList<>();
  private final Set<InflightActivateJobsRequest<T>> activeRequestsToBeRepeated = new HashSet<>();
  private final AtomicInteger failedAttempts = new AtomicInteger();
  private long lastUpdatedTime;

  public InFlightLongPollingActivateJobsRequestsState(
      final String jobType, final LongPollingMetrics metrics) {
    this.jobType = jobType;
    this.metrics = metrics;
  }

  public void incrementFailedAttempts(final long lastUpdatedTime) {
    failedAttempts.incrementAndGet();
    this.lastUpdatedTime = lastUpdatedTime;
  }

  public boolean shouldAttempt(final int attemptThreshold) {
    return failedAttempts.get() < attemptThreshold;
  }

  public void resetFailedAttempts() {
    setFailedAttempts(0);
  }

  public int getFailedAttempts() {
    return failedAttempts.get();
  }

  public void setFailedAttempts(final int failedAttempts) {
    this.failedAttempts.set(failedAttempts);
    if (failedAttempts == 0) {
      activeRequestsToBeRepeated.addAll(activeRequests);
    }
  }

  public long getLastUpdatedTime() {
    return lastUpdatedTime;
  }

  public void enqueueRequest(final InflightActivateJobsRequest<T> request) {
    if (!pendingRequests.contains(request)) {
      pendingRequests.offer(request);
    }
    updatePendingMetrics();
  }

  public void removeRequest(final InflightActivateJobsRequest<T> request) {
    pendingRequests.remove(request);
    updatePendingMetrics();
  }

  public InflightActivateJobsRequest<T> getNextPendingRequest() {
    final InflightActivateJobsRequest<T> request = pendingRequests.poll();
    updatePendingMetrics();
    return request;
  }

  public void addActiveRequest(final InflightActivateJobsRequest<T> request) {
    activeRequests.offer(request);
    pendingRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
    updatePendingMetrics();
  }

  public void removeActiveRequest(final InflightActivateJobsRequest<T> request) {
    activeRequests.remove(request);
    activeRequestsToBeRepeated.remove(request);
  }

  public boolean hasActiveRequests() {
    return !activeRequests.isEmpty();
  }

  private void updatePendingMetrics() {
    metrics.setBlockedRequestsCount(jobType, pendingRequests.size());
  }

  /**
   * Returns whether the request should be repeated. A request should be repeated if the failed
   * attempts were reset to 0 (because new jobs became available) whilst the request was running,
   * and if the request's long polling is enabled.
   */
  public boolean shouldBeRepeated(final InflightActivateJobsRequest<T> request) {
    return activeRequestsToBeRepeated.contains(request) && !request.isLongPollingDisabled();
  }
}
