/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

public class ExecutableEndEvent extends ExecutableFlowNode implements ExecutableJobWorkerElement {

  private JobWorkerProperties jobWorkerProperties;
  private ExecutableError error;
  private ExecutableEscalation escalation;

  private ExecutableSignal signal;

  private boolean isTerminateEndEvent;

  public ExecutableEndEvent(final String id) {
    super(id);
  }

  public ExecutableError getError() {
    return error;
  }

  public void setError(final ExecutableError error) {
    this.error = error;
  }

  public ExecutableEscalation getEscalation() {
    return escalation;
  }

  public void setEscalation(final ExecutableEscalation escalation) {
    this.escalation = escalation;
  }

  public ExecutableSignal getSignal() {
    return signal;
  }

  public void setSignal(final ExecutableSignal signal) {
    this.signal = signal;
  }

  @Override
  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  @Override
  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }

  public boolean isNoneEndEvent() {
    return !isErrorEndEvent()
        && !isMessageEndEvent()
        && !isTerminateEndEvent
        && !isEscalationEndEvent()
        && !isSignalEndEvent();
  }

  public boolean isErrorEndEvent() {
    return error != null;
  }

  public boolean isEscalationEndEvent() {
    return escalation != null;
  }

  public boolean isMessageEndEvent() {
    return jobWorkerProperties != null;
  }

  public boolean isSignalEndEvent() {
    return signal != null;
  }

  public boolean isTerminateEndEvent() {
    return isTerminateEndEvent;
  }

  public void setTerminateEndEvent(final boolean isTerminateEndEvent) {
    this.isTerminateEndEvent = isTerminateEndEvent;
  }
}
