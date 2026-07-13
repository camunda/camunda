/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;

public class WorkerProperties {

  // Worker connection properties (jobType, workerName, threads, capacity, pollingDelay,
  // streamEnabled, timeout) are configured via camunda.client.worker.defaults.* and
  // auto-applied by the camunda-spring-boot-starter. Only properties consumed directly
  // by Worker.java are kept here.

  private Duration completionDelay = Duration.ofMillis(300);
  private String payloadPath = "bpmn/big_payload.json";
  private boolean sendMessage = false;
  private String messageName = "messageName";
  private String correlationKeyVariableName = "correlationKey-var";
  private double incidentRatio = 0;

  public Duration getCompletionDelay() {
    return completionDelay;
  }

  public void setCompletionDelay(final Duration completionDelay) {
    this.completionDelay = completionDelay;
  }

  public String getPayloadPath() {
    return payloadPath;
  }

  public void setPayloadPath(final String payloadPath) {
    this.payloadPath = payloadPath;
  }

  public boolean isSendMessage() {
    return sendMessage;
  }

  public void setSendMessage(final boolean sendMessage) {
    this.sendMessage = sendMessage;
  }

  public String getMessageName() {
    return messageName;
  }

  public void setMessageName(final String messageName) {
    this.messageName = messageName;
  }

  public String getCorrelationKeyVariableName() {
    return correlationKeyVariableName;
  }

  public void setCorrelationKeyVariableName(final String correlationKeyVariableName) {
    this.correlationKeyVariableName = correlationKeyVariableName;
  }

  /**
   * The fraction of jobs the worker deliberately fails with zero retries in order to mint incidents
   * (the {@code incident generation ratio}). {@code 0} (the default) disables incident generation;
   * {@code 0.01} mirrors the reported customer shape (~1%); higher values force a backlog faster
   * during bring-up. The failing fraction is selected deterministically per job key.
   */
  public double getIncidentRatio() {
    return incidentRatio;
  }

  public void setIncidentRatio(final double incidentRatio) {
    this.incidentRatio = incidentRatio;
  }
}
