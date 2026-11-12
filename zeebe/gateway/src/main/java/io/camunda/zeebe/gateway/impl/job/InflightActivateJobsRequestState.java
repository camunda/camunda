/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.gateway.metrics.JobActivationPollStats;
import java.util.Map;
import java.util.TreeMap;

public class InflightActivateJobsRequestState {

  private final PartitionIdIterator iterator;
  private int remainingAmount;
  private boolean pollPrevPartition;
  private boolean resourceExhaustedWasPresent;
  private int requests;
  private final Map<Integer, Integer> jobsPerPartition = new TreeMap<>();

  public InflightActivateJobsRequestState(
      final PartitionIdIterator iterator, final int remainingAmount) {
    this.iterator = iterator;
    this.remainingAmount = remainingAmount;
  }

  private boolean hasNextPartition() {
    return iterator.hasNext();
  }

  public int getCurrentPartition() {
    return iterator.getCurrentPartitionId();
  }

  public int getNextPartition() {
    return pollPrevPartition ? iterator.getCurrentPartitionId() : iterator.next();
  }

  public int getRemainingAmount() {
    return remainingAmount;
  }

  public void setRemainingAmount(int remainingAmount) {
    this.remainingAmount = remainingAmount;
  }

  public boolean wasResourceExhaustedPresent() {
    return resourceExhaustedWasPresent;
  }

  public void setResourceExhaustedWasPresent(final boolean resourceExhaustedWasPresent) {
    this.resourceExhaustedWasPresent = resourceExhaustedWasPresent;
  }

  public void setPollPrevPartition(boolean pollPrevPartition) {
    this.pollPrevPartition = pollPrevPartition;
  }

  public boolean shouldActivateJobs() {
    return remainingAmount > 0 && (pollPrevPartition || hasNextPartition());
  }

  /** Counts a request to the partition, for {@link #pollStats()}. */
  public void recordRequest(final int partitionId) {
    requests++;
    jobsPerPartition.putIfAbsent(partitionId, 0);
  }

  /** Counts jobs received from the partition, for {@link #pollStats()}. */
  public void recordJobs(final int partitionId, final int jobs) {
    jobsPerPartition.merge(partitionId, jobs, Integer::sum);
  }

  /** Every request waits for the one before it, so there are as many rounds as requests. */
  public JobActivationPollStats pollStats() {
    return new JobActivationPollStats(false, requests, requests, Map.copyOf(jobsPerPartition));
  }
}
