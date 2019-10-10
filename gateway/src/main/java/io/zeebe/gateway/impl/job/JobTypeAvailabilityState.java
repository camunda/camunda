/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.metrics.LongPollingMetrics;
import java.util.LinkedList;
import java.util.Queue;

public class JobTypeAvailabilityState {

  private final String jobType;
  private final LongPollingMetrics metrics;
  private final Queue<LongPollingActivateJobsRequest> blockedRequests = new LinkedList<>();
  private int emptyResponses;
  private long lastUpdatedTime;

  public JobTypeAvailabilityState(String jobType, LongPollingMetrics metrics) {
    this.jobType = jobType;
    this.metrics = metrics;
  }

  public void incrementEmptyResponses(long lastUpdatedTime) {
    emptyResponses++;
    this.lastUpdatedTime = lastUpdatedTime;
  }

  public void resetEmptyResponses(int value) {
    this.emptyResponses = value;
  }

  public int getEmptyResponses() {
    return emptyResponses;
  }

  public long getLastUpdatedTime() {
    return lastUpdatedTime;
  }

  public void blockRequest(LongPollingActivateJobsRequest request) {
    blockedRequests.offer(request);
    metrics.setBlockedRequestsCount(jobType, blockedRequests.size());
  }

  public void clearBlockedRequests() {
    blockedRequests.clear();
    metrics.setBlockedRequestsCount(jobType, 0);
  }

  public void removeCanceledRequests() {
    blockedRequests.removeIf(LongPollingActivateJobsRequest::isCanceled);
    metrics.setBlockedRequestsCount(jobType, blockedRequests.size());
  }

  public void removeBlockedRequest(LongPollingActivateJobsRequest request) {
    blockedRequests.remove(request);
    metrics.setBlockedRequestsCount(jobType, blockedRequests.size());
  }

  public LongPollingActivateJobsRequest pollBlockedRequests() {
    final LongPollingActivateJobsRequest request = blockedRequests.poll();
    metrics.setBlockedRequestsCount(jobType, blockedRequests.size());
    return request;
  }

  public Queue<LongPollingActivateJobsRequest> getBlockedRequests() {
    return blockedRequests;
  }
}
