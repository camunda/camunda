/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "load-tester")
public class LoadTesterProperties {

  private boolean monitorDataAvailability = true;
  private Duration monitorDataAvailabilityInterval = Duration.ofMillis(250);
  private boolean performReadBenchmarks = false;

  @NestedConfigurationProperty private StarterProperties starter = new StarterProperties();

  @NestedConfigurationProperty private WorkerProperties worker = new WorkerProperties();

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

  public boolean isPerformReadBenchmarks() {
    return performReadBenchmarks;
  }

  public void setPerformReadBenchmarks(final boolean performReadBenchmarks) {
    this.performReadBenchmarks = performReadBenchmarks;
  }

  public StarterProperties getStarter() {
    return starter;
  }

  public void setStarter(final StarterProperties starter) {
    this.starter = starter;
  }

  public WorkerProperties getWorker() {
    return worker;
  }

  public void setWorker(final WorkerProperties worker) {
    this.worker = worker;
  }
}
