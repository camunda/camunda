/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public class ExecutableEndEvent extends ExecutableFlowNode implements ExecutableJobWorkerElement {

  private JobWorkerProperties jobWorkerProperties;
  private ExecutableError error;
  private ExecutableEscalation escalation;

  private ExecutableSignal signal;

  private boolean isTerminateEndEvent;

  private ExecutableCompensation compensation;

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

  public ExecutableCompensation getCompensation() {
    return compensation;
  }

  public void setCompensation(final ExecutableCompensation compensation) {
    this.compensation = compensation;
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
        && !isSignalEndEvent()
        && !isCompensationEvent();
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

  public boolean isCompensationEvent() {
    return getEventType() == BpmnEventType.COMPENSATION;
  }
}
