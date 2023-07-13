/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

public class TransitionStepContext {

  private Long timeOfLastCompleteStepTransition;

  private String currentStepTransition;

  private boolean isPartitionInTransition;

  public TransitionStepContext() {
    isPartitionInTransition = false;
    currentStepTransition = null;
    timeOfLastCompleteStepTransition = null;
  }

  public Long getTimeOfLastTransitionStep() {
    return timeOfLastCompleteStepTransition;
  }

  public void setTimeOfLastCompleteStepTransition(final Long timeOfLastCompleteStepTransition) {
    this.timeOfLastCompleteStepTransition = timeOfLastCompleteStepTransition;
  }

  public String getCurrentStepTransition() {
    return currentStepTransition;
  }

  public void setCurrentStepTransition(final String currentStepTransition) {
    this.currentStepTransition = currentStepTransition;
  }

  public boolean getIsPartitionInTransition() {
    return isPartitionInTransition;
  }

  public void setIsPartitionInTransition(final boolean isPartitionInTransition) {
    this.isPartitionInTransition = isPartitionInTransition;
  }
}
