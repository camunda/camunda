/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;
import java.util.List;

public class StarterCfg {

  private String processId;
  private int rate;
  private int threads;

  /** Paths are relative to classpath. */
  private String bpmnXmlPath;

  private List<String> extraBpmnModels;

  private String businessKey;

  private String payloadPath;
  private boolean withResults;
  private Duration withResultsTimeout;

  private int durationLimit;

  private boolean startViaMessage;
  private String msgName;

  private double rateIncreaseFactor;
  private double rateDecreaseFactor;
  private int rateAdjustmentIntervalMs;

  public boolean isStartViaMessage() {
    return startViaMessage;
  }

  public void setStartViaMessage(final boolean startViaMessage) {
    this.startViaMessage = startViaMessage;
  }

  public String getMsgName() {
    return msgName;
  }

  public void setMsgName(final String msgName) {
    this.msgName = msgName;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public int getRate() {
    return rate;
  }

  public void setRate(final int rate) {
    this.rate = rate;
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(final int threads) {
    this.threads = threads;
  }

  public List<String> getExtraBpmnModels() {
    return extraBpmnModels;
  }

  public void setExtraBpmnModels(final List<String> extraBpmnModels) {
    this.extraBpmnModels = extraBpmnModels;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public String getBpmnXmlPath() {
    return bpmnXmlPath;
  }

  public void setBpmnXmlPath(final String bpmnXmlPath) {
    this.bpmnXmlPath = bpmnXmlPath;
  }

  public String getPayloadPath() {
    return payloadPath;
  }

  public void setPayloadPath(final String payloadPath) {
    this.payloadPath = payloadPath;
  }

  public boolean isWithResults() {
    return withResults;
  }

  public void setWithResults(final boolean withResults) {
    this.withResults = withResults;
  }

  public Duration getWithResultsTimeout() {
    return withResultsTimeout;
  }

  public void setWithResultsTimeout(final Duration withResultsTimeout) {
    this.withResultsTimeout = withResultsTimeout;
  }

  public int getDurationLimit() {
    return durationLimit;
  }

  public void setDurationLimit(final int durationLimit) {
    this.durationLimit = durationLimit;
  }

  public double getRateIncreaseFactor() {
    return rateIncreaseFactor;
  }

  public void setRateIncreaseFactor(final double rateIncreaseFactor) {
    this.rateIncreaseFactor = rateIncreaseFactor;
  }

  public double getRateDecreaseFactor() {
    return rateDecreaseFactor;
  }

  public void setRateDecreaseFactor(final double rateDecreaseFactor) {
    this.rateDecreaseFactor = rateDecreaseFactor;
  }

  public int getRateAdjustmentIntervalMs() {
    return rateAdjustmentIntervalMs;
  }

  public void setRateAdjustmentIntervalMs(final int rateAdjustmentIntervalMs) {
    this.rateAdjustmentIntervalMs = rateAdjustmentIntervalMs;
  }
}
