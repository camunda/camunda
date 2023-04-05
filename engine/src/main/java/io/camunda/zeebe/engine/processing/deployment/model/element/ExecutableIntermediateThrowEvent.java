/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

public class ExecutableIntermediateThrowEvent extends ExecutableFlowNode
    implements ExecutableJobWorkerElement {

  private JobWorkerProperties jobWorkerProperties;

  private ExecutableLink link;

  private ExecutableEscalation escalation;

  private ExecutableSignal signal;

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

  public boolean isNoneThrowEvent() {
    return !isMessageThrowEvent()
        && !isLinkThrowEvent()
        && !isEscalationThrowEvent()
        && !isSignalThrowEvent();
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
}
