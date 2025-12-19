/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import com.typesafe.config.Optional;
import java.time.Duration;

public class AppCfg {

  private String brokerUrl;
  private String brokerRestUrl;
  private String brokerManagementUrl;
  private boolean preferRest;
  private int monitoringPort;
  private StarterCfg starter;
  @Optional private BenchmarkCfg benchmark;
  private WorkerCfg worker;
  private AuthCfg auth;
  private boolean monitorDataAvailability = true;
  private Duration monitorDataAvailabilityInterval = Duration.ofMillis(250);

  public String getBrokerUrl() {
    return brokerUrl;
  }

  public void setBrokerUrl(final String brokerUrl) {
    this.brokerUrl = brokerUrl;
  }

  public String getBrokerRestUrl() {
    return brokerRestUrl;
  }

  public void setBrokerRestUrl(final String brokerRestUrl) {
    this.brokerRestUrl = brokerRestUrl;
  }

  public String getBrokerManagementUrl() {
    return brokerManagementUrl;
  }

  public void setBrokerManagementUrl(final String brokerManagementUrl) {
    this.brokerManagementUrl = brokerManagementUrl;
  }

  public boolean isPreferRest() {
    return preferRest;
  }

  public void setPreferRest(final boolean preferRest) {
    this.preferRest = preferRest;
  }

  public BenchmarkCfg getBenchmark() {
    return benchmark;
  }

  public void setBenchmark(final BenchmarkCfg benchmark) {
    this.benchmark = benchmark;
  }

  public StarterCfg getStarter() {
    return starter;
  }

  public void setStarter(final StarterCfg starter) {
    this.starter = starter;
  }

  public WorkerCfg getWorker() {
    return worker;
  }

  public void setWorker(final WorkerCfg worker) {
    this.worker = worker;
  }

  public int getMonitoringPort() {
    return monitoringPort;
  }

  public void setMonitoringPort(final int monitoringPort) {
    this.monitoringPort = monitoringPort;
  }

  public AuthCfg getAuth() {
    return auth;
  }

  public void setAuth(final AuthCfg auth) {
    this.auth = auth;
  }

  public boolean isMonitorDataAvailability() {
    return monitorDataAvailability;
  }

  public void setMonitorDataAvailability(final boolean monitorDataAvailability) {
    this.monitorDataAvailability = monitorDataAvailability;
  }

  public Duration getMonitorDataAvailabilityInterval() {
    return monitorDataAvailabilityInterval;
  }

  public void setMonitorDataAvailabilityInterval(final Duration monitorDataAvailabilityInterval) {
    this.monitorDataAvailabilityInterval = monitorDataAvailabilityInterval;
  }
}
