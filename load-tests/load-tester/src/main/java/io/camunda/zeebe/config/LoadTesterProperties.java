/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("load-tester")
public class LoadTesterProperties {

  private boolean monitorDataAvailability = true;
  private Duration monitorDataAvailabilityInterval = Duration.ofMillis(250);
  private boolean performReadBenchmarks = false;
  private String disabledQueries = "";

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

  public String getDisabledQueries() {
    return disabledQueries;
  }

  public void setDisabledQueries(final String disabledQueries) {
    this.disabledQueries = disabledQueries;
  }

  public List<String> getDisabledQueriesList() {
    if (disabledQueries == null || disabledQueries.isBlank()) {
      return List.of();
    }
    return Arrays.stream(disabledQueries.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }
}
