/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import lombok.Data;

@Data
public class MixpanelHeartbeatMetrics {

  private long processReportCount;
  private long decisionReportCount;
  private long dashboardCount;
  private long reportShareCount;
  private long dashboardShareCount;
  private long alertCount;
  private long taskReportCount;

  public MixpanelHeartbeatMetrics(
      long processReportCount,
      long decisionReportCount,
      long dashboardCount,
      long reportShareCount,
      long dashboardShareCount,
      long alertCount,
      long taskReportCount) {
    this.processReportCount = processReportCount;
    this.decisionReportCount = decisionReportCount;
    this.dashboardCount = dashboardCount;
    this.reportShareCount = reportShareCount;
    this.dashboardShareCount = dashboardShareCount;
    this.alertCount = alertCount;
    this.taskReportCount = taskReportCount;
  }
}
