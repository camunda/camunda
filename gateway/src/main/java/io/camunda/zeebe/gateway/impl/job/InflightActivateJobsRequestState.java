/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.impl.broker.PartitionIdIterator;

public class InflightActivateJobsRequestState {

  private final PartitionIdIterator iterator;
  private int remainingAmount;
  private boolean pollPrevPartition;
  private boolean resourceExhaustedWasPresent;

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
}
