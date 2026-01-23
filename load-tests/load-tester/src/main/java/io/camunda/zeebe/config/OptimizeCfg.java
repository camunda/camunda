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

public class OptimizeCfg {

  private String baseUrl;
  private List<String> reportPaths;
  private Duration interval;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public List<String> getReportPaths() {
    return reportPaths;
  }

  public void setReportPaths(final List<String> reportPaths) {
    this.reportPaths = reportPaths;
  }

  public Duration getInterval() {
    return interval;
  }

  public void setInterval(final Duration interval) {
    this.interval = interval;
  }
}
