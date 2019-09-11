/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import java.util.LinkedList;
import java.util.Queue;

public class JobTypeAvailabilityState {

  private final Queue<LongPollingActivateJobsRequest> blockedRequests = new LinkedList<>();
  private int emptyResponses;
  private long lastUpdatedTime;

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
  }

  public void clearBlockedRequests() {
    blockedRequests.clear();
  }

  public void removeCanceledRequests() {
    blockedRequests.removeIf(LongPollingActivateJobsRequest::isCanceled);
  }

  public void removeBlockedRequest(LongPollingActivateJobsRequest request) {
    blockedRequests.remove(request);
  }

  public LongPollingActivateJobsRequest pollBlockedRequests() {
    return blockedRequests.poll();
  }

  public Queue<LongPollingActivateJobsRequest> getBlockedRequests() {
    return blockedRequests;
  }
}
