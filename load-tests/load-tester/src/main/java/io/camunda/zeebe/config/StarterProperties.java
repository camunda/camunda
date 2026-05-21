/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class StarterProperties {

  private String processId = "benchmark";
  private double rate = 300;
  private Duration rateDuration = Duration.ofSeconds(1);
  private int threads = 2;
  private String bpmnXmlPath = "bpmn/one_task.bpmn";
  private List<String> extraBpmnModels = List.of();
  private String businessKey = "businessKey";
  private String payloadPath = "bpmn/big_payload.json";
  private boolean withResults = false;
  private Duration withResultsTimeout = Duration.ofSeconds(60);
  private int durationLimit = 0;
  private boolean startViaMessage = false;
  private String msgName = "msg";

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public double getRate() {
    return rate;
  }

  public void setRate(final double rate) {
    this.rate = rate;
  }

  public Duration getRateDuration() {
    return rateDuration;
  }

  public void setRateDuration(final Duration rateDuration) {
    this.rateDuration = rateDuration;
  }

  public double getRatePerSecond() {
    return rate / (rateDuration.toNanos() / 1_000_000_000.0);
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(final int threads) {
    this.threads = threads;
  }

  public String getBpmnXmlPath() {
    return bpmnXmlPath;
  }

  public void setBpmnXmlPath(final String bpmnXmlPath) {
    this.bpmnXmlPath = bpmnXmlPath;
  }

  public List<String> getExtraBpmnModels() {
    if (extraBpmnModels.isEmpty()) {
      // Workaround: Spring Boot's relaxed binding cannot map indexed env vars like
      // LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_0_ to load-tester.starter.extra-bpmn-models[0]
      // because the underscore in LOAD_TESTER is ambiguous (could be load.tester or load-tester).
      // Fall back to reading the indexed env vars directly.
      final List<String> fromEnv = new ArrayList<>();
      for (int i = 0; ; i++) {
        final String val = System.getenv("LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_" + i + "_");
        if (val == null || val.isBlank()) {
          break;
        }
        fromEnv.add(val);
      }
      if (!fromEnv.isEmpty()) {
        extraBpmnModels = fromEnv;
      }
    }
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
}
