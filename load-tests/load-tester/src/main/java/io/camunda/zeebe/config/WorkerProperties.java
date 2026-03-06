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

  private String jobType = "benchmark-task";
  private String workerName = "benchmark-worker";
  private int threads = 10;
  private int capacity = 30;
  private Duration pollingDelay = Duration.ofSeconds(1);
  private Duration completionDelay = Duration.ofMillis(300);
  private String payloadPath = "bpmn/big_payload.json";
  private boolean streamEnabled = true;
  private Duration timeout = Duration.ZERO;
  private boolean sendMessage = false;
  private String messageName = "messageName";
  private String correlationKeyVariableName = "correlationKey-var";

  public String getJobType() {
    return jobType;
  }

  public void setJobType(final String jobType) {
    this.jobType = jobType;
  }

  public String getWorkerName() {
    return workerName;
  }

  public void setWorkerName(final String workerName) {
    this.workerName = workerName;
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(final int threads) {
    this.threads = threads;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(final int capacity) {
    this.capacity = capacity;
  }

  public Duration getPollingDelay() {
    return pollingDelay;
  }

  public void setPollingDelay(final Duration pollingDelay) {
    this.pollingDelay = pollingDelay;
  }

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

  public boolean isStreamEnabled() {
    return streamEnabled;
  }

  public void setStreamEnabled(final boolean streamEnabled) {
    this.streamEnabled = streamEnabled;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(final Duration timeout) {
    this.timeout = timeout;
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
}
