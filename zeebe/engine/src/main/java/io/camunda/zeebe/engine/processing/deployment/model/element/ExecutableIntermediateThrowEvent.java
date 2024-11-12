/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public class ExecutableIntermediateThrowEvent extends ExecutableFlowNode
    implements ExecutableJobWorkerElement {

  private JobWorkerProperties jobWorkerProperties;

  private ExecutableLink link;

  private ExecutableEscalation escalation;

  private ExecutableSignal signal;

  private ExecutableCompensation compensation;

  public ExecutableIntermediateThrowEvent(final String id) {
    super(id);
  }

  @Override
  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  @Override
  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }

  public ExecutableLink getLink() {
    return link;
  }

  public void setLink(final ExecutableLink link) {
    this.link = link;
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

  public boolean isNoneThrowEvent() {
    return !isMessageThrowEvent()
        && !isLinkThrowEvent()
        && !isEscalationThrowEvent()
        && !isSignalThrowEvent()
        && !isCompensationEvent();
  }

  public boolean isMessageThrowEvent() {
    return jobWorkerProperties != null;
  }

  public boolean isLinkThrowEvent() {
    return link != null;
  }

  public boolean isEscalationThrowEvent() {
    return escalation != null;
  }

  public boolean isSignalThrowEvent() {
    return signal != null;
  }

  public boolean isCompensationEvent() {
    return getEventType() == BpmnEventType.COMPENSATION;
  }
}
